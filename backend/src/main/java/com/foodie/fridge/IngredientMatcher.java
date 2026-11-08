package com.foodie.fridge;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 食材名匹配器。
 *
 * <p><b>这是「按冰箱推荐」里最容易出错的一环。</b>用户在冰箱里写「五花肉」，
 * 而菜谱里写的是「带皮五花肉」；写「西红柿」，菜谱写「番茄」。
 * 如果只做字符串相等，匹配率会低到让功能看起来是坏的。
 *
 * <p>所以匹配分三步走，每一步都只解决一类问题：
 *
 * <pre>
 *   ① 归一化   去空白 + 拉丁转小写            " 姜 片 " → "姜片"
 *   ② 形态剥离 剥掉 末/丝/丁/片/块/段/瓣/碎    "姜片" → "姜"
 *   ③ 同义词   收敛到菜谱里常用的那个写法      "西红柿" → "番茄"
 * </pre>
 *
 * <p>然后判三级：<b>精确 / 近似 / 没有</b>。近似匹配在 UI 上会标「≈」，
 * 让用户自己判断——这和成本模块里把估算行标出来是同一个原则：
 * 宁可展示「不确定」，也不要悄悄给一个错结论。
 *
 * <p><b>两道护栏（没有它们这个功能就不可信）：</b>
 * <ol>
 *   <li><b>长度护栏</b>：短名只有 1 个字时不参与「包含」匹配。
 *       否则「葱」会匹配上「洋葱」、「蒜」会匹配上「蒜苗」——这两个错误
 *       恰好都是「有 A 却说能做需要 B 的菜」，属于最该避免的误报。</li>
 *   <li><b>加工形态护栏</b>：当菜谱要的是「A + 加工后缀」时，只有 A 不算命中。
 *       有番茄不等于有番茄酱，有花生不等于有花生油。
 *       护栏只对「菜谱侧更长」生效：反过来「冰箱里有辣椒面」用来做需要
 *       「辣椒」的菜是合理的替代，不该被拦。</li>
 * </ol>
 *
 * <p>同义词表是<b>烹饪等价</b>表而不是植物学分类表，初始只收录
 * 高频且低争议的几组。扩展方式：往 {@link #SYNONYM} 里加一条即可，
 * 不需要改数据库或任何调用方。
 */
public final class IngredientMatcher {

    /** 匹配级别。 */
    public enum Tier {
        /** 归一化后完全相等。 */
        EXACT,
        /** 双方存在包含关系，属于「大概率能用」的近似。 */
        APPROX
    }

    /** 形态后缀：这些是同一食材的切法，剥掉后应视为同一个东西。 */
    private static final List<String> FORM_SUFFIX = List.of(
            "末", "丝", "丁", "片", "块", "段", "瓣", "碎");

    /**
     * 加工形态后缀：菜谱要「A + 这些后缀」时，光有 A 不算命中。
     *
     * <p>例：「番茄酱」= 番茄 + 酱，有番茄做不出番茄酱；
     * 「花生油」= 花生 + 油，有花生也不等于有花生油。
     */
    private static final List<String> PROCESSED_SUFFIX = List.of(
            "酱", "膏", "粉", "汁", "油", "苗", "芽", "精", "露", "面");

    /**
     * 同义词收敛表：{@code 别名 → 菜谱里常用的写法}。
     *
     * <p>只收「换了名字但确实是同一个东西」的组合。
     * 像「嫩豆腐」与「豆腐」这种包含关系交给近似匹配处理，不必占用同义词表。
     */
    private static final Map<String, String> SYNONYM = Map.ofEntries(
            // 蔬菜
            Map.entry("西红柿", "番茄"),
            Map.entry("马铃薯", "土豆"),
            Map.entry("洋芋", "土豆"),
            Map.entry("柿子椒", "青椒"),
            // 葱蒜姜：菜谱里写法最杂的一组
            Map.entry("蒜", "大蒜"),
            Map.entry("生姜", "姜"),
            Map.entry("小葱", "葱"),
            Map.entry("香葱", "葱"),
            Map.entry("葱花", "葱"),
            Map.entry("葱白", "葱"),
            Map.entry("大葱", "葱"),
            // 肉类别名
            Map.entry("猪里脊肉", "猪里脊"),
            Map.entry("猪排骨", "排骨"),
            Map.entry("可生食鸡蛋", "鸡蛋"),
            Map.entry("鸡蛋清", "蛋清"),
            // 糖与淀粉：做菜时完全可互换
            Map.entry("白砂糖", "白糖"),
            Map.entry("细砂糖", "白糖"),
            Map.entry("绵白糖", "白糖"),
            // 单字调料：只有 1 个字的名字过不了「包含匹配」的长度护栏，必须靠同义词表收敛
            Map.entry("食盐", "盐"),
            Map.entry("玉米淀粉", "淀粉"),
            Map.entry("干淀粉", "淀粉"),
            Map.entry("水淀粉", "淀粉"),
            Map.entry("生粉", "淀粉"),
            // 料酒类
            Map.entry("绍酒", "料酒"));

    /** 短于这个长度的名字不参与包含匹配（见长度护栏）。 */
    private static final int MIN_FUZZY_LENGTH = 2;

    /** 最多剥几层形态后缀，防止极端输入把名字剥空。 */
    private static final int MAX_STRIP_ROUNDS = 3;

    private IngredientMatcher() {
    }

    /** 去空白 + 拉丁转小写。中文不受大小写影响，这一步主要处理「G」「g」这类单位写法。 */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (!Character.isWhitespace(ch)) {
                builder.append(ch);
            }
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    /** 归一化 + 形态剥离 + 同义词收敛，得到用于比较的规范写法。 */
    public static String canonical(String raw) {
        String name = normalize(raw);
        if (name.isEmpty()) {
            return "";
        }
        name = stripFormSuffix(name);
        return SYNONYM.getOrDefault(name, name);
    }

    /**
     * 判断冰箱里的某样食材能否覆盖菜谱里的某个食材。
     *
     * @return {@link Tier#EXACT} / {@link Tier#APPROX}；无法匹配时返回 {@code null}
     */
    public static Tier match(String fridgeName, String ingredientName) {
        String fridge = canonical(fridgeName);
        String ingredient = canonical(ingredientName);
        if (fridge.isEmpty() || ingredient.isEmpty()) {
            return null;
        }
        if (fridge.equals(ingredient)) {
            return Tier.EXACT;
        }

        String longer;
        String shorter;
        boolean longerIsIngredient;
        if (ingredient.contains(fridge)) {
            longer = ingredient;
            shorter = fridge;
            longerIsIngredient = true;
        } else if (fridge.contains(ingredient)) {
            longer = fridge;
            shorter = ingredient;
            longerIsIngredient = false;
        } else {
            return null;
        }

        // 护栏一：单字不做包含匹配
        if (shorter.length() < MIN_FUZZY_LENGTH) {
            return null;
        }
        // 护栏二：菜谱侧多出来的部分是「加工形态」时不算命中
        if (longerIsIngredient && PROCESSED_SUFFIX.contains(longer.replace(shorter, ""))) {
            return null;
        }
        return Tier.APPROX;
    }

    /** 反复剥掉形态后缀，但至少保留 1 个字，避免「蒜片」被剥成空串。 */
    private static String stripFormSuffix(String name) {
        String current = name;
        for (int round = 0; round < MAX_STRIP_ROUNDS; round++) {
            String stripped = null;
            for (String suffix : FORM_SUFFIX) {
                if (current.length() > suffix.length() && current.endsWith(suffix)) {
                    stripped = current.substring(0, current.length() - suffix.length());
                    break;
                }
            }
            if (stripped == null) {
                return current;
            }
            current = stripped;
        }
        return current;
    }
}
