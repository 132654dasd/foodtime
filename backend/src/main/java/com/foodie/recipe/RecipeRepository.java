package com.foodie.recipe;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeRepository extends JpaRepository<Recipe, Long>, JpaSpecificationExecutor<Recipe> {

    // ------------------------------------------------------------ 列表检索

    /**
     * 无关键词时的列表检索。
     *
     * <p>用 {@code :param is null or ...} 而不是 Specification：
     * 这里的条件固定且简单，写成一条 JPQL 更好读、也更好审。
     * 带关键词的检索走 {@link #searchByFulltext}。
     */
    @Query("""
            select r from Recipe r
            where (:cuisine is null or r.cuisine = :cuisine)
              and (:category is null or r.category = :category)
              and (:difficulty is null or r.difficulty = :difficulty)
              and (:maxMinutes is null or r.cookMinutes <= :maxMinutes)
            """)
    Page<Recipe> browse(@Param("cuisine") String cuisine,
                        @Param("category") DishCategory category,
                        @Param("difficulty") Difficulty difficulty,
                        @Param("maxMinutes") Integer maxMinutes,
                        Pageable pageable);

    /**
     * 中文关键词检索，走 MySQL ngram 全文索引。
     *
     * <p>为什么必须是 nativeQuery：
     * {@code MATCH ... AGAINST} 没有 JPQL 等价写法，
     * 而 {@code LIKE '%kw%'} 因为前导通配符完全用不上索引，
     * 在几万条菜谱上就是全表扫描。ngram 解析器让中文也能走倒排索引。
     *
     * <p>代价是这条路径在 H2 上跑不了，必须由打真实 MySQL 的集成测试覆盖。
     *
     * <p>排序固定按相关度输出，因此调用方必须传 <b>unsorted</b> 的 Pageable，
     * 否则 Spring Data 追加的 ORDER BY 会覆盖掉相关度排序。
     */
    @Query(value = """
            select r.* from recipe r
            where match(r.title, r.description) against (:keyword in natural language mode)
              and (:cuisine is null or r.cuisine = :cuisine)
              and (:category is null or r.category = :category)
              and (:difficulty is null or r.difficulty = :difficulty)
              and (:maxMinutes is null or r.cook_minutes <= :maxMinutes)
            order by match(r.title, r.description) against (:keyword in natural language mode) desc
            """,
            countQuery = """
            select count(*) from recipe r
            where match(r.title, r.description) against (:keyword in natural language mode)
              and (:cuisine is null or r.cuisine = :cuisine)
              and (:category is null or r.category = :category)
              and (:difficulty is null or r.difficulty = :difficulty)
              and (:maxMinutes is null or r.cook_minutes <= :maxMinutes)
            """,
            nativeQuery = true)
    Page<Recipe> searchByFulltext(@Param("keyword") String keyword,
                                  @Param("cuisine") String cuisine,
                                  @Param("category") String category,
                                  @Param("difficulty") String difficulty,
                                  @Param("maxMinutes") Integer maxMinutes,
                                  Pageable pageable);

    List<Recipe> findByIdIn(Collection<Long> ids);

    // ------------------------------------------------------------ 随机配餐候选池

    /**
     * 拉取候选池的轻量投影。
     *
     * <p>只选加权抽样需要的 6 个标量列，不碰 steps/ingredients 关联，
     * 于是一次查询就能把候选池读进内存，忌口剔除与抽样都在 Java 侧完成。
     */
    @Query("""
            select new com.foodie.recipe.RecipeCandidate(
                       r.id, r.category, r.cookMinutes, r.difficultyLevel, r.viewCount, r.favoriteCount)
            from Recipe r
            where r.category in :categories
            """)
    List<RecipeCandidate> findCandidates(@Param("categories") Collection<DishCategory> categories);

    // ------------------------------------------------------------ 忌口反查

    /** 菜名命中忌口关键词的菜谱 id。 */
    @Query("select r.id from Recipe r where r.title like concat('%', :keyword, '%')")
    List<Long> findIdsByTitleContaining(@Param("keyword") String keyword);

    /** 标签命中忌口关键词的菜谱 id（花生/坚果/乳制品这类看不出来的过敏原靠它兜底）。 */
    @Query("select distinct r.id from Recipe r join r.tags t where t.name like concat('%', :keyword, '%')")
    List<Long> findIdsByTagContaining(@Param("keyword") String keyword);

    // ------------------------------------------------------------ 字典与统计

    @Query("select distinct r.cuisine from Recipe r order by r.cuisine")
    List<String> findDistinctCuisines();

    @Query("select r.category, count(r) from Recipe r group by r.category")
    List<Object[]> countGroupByCategory();

    @Query("select r.difficulty, count(r) from Recipe r group by r.difficulty")
    List<Object[]> countGroupByDifficulty();

    @Query("select count(r) from Recipe r where r.cookMinutes <= :minutes")
    long countByMaxMinutes(@Param("minutes") int minutes);

    // ------------------------------------------------------------ 计数与评分

    /**
     * 原子累加浏览量。
     *
     * <p>不读实体、不加锁，靠数据库的 {@code col = col + ?} 保证并发安全，
     * 这是「读-改-写」在并发下丢更新的经典修复方式。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Recipe r set r.viewCount = r.viewCount + :delta where r.id = :id")
    int addViewCount(@Param("id") Long id, @Param("delta") int delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Recipe r set r.favoriteCount = r.favoriteCount + :delta where r.id = :id")
    int addFavoriteCount(@Param("id") Long id, @Param("delta") int delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Recipe r
               set r.ratingCount = :count, r.ratingAvg = :avg
             where r.id = :id
            """)
    int updateRatingStats(@Param("id") Long id,
                          @Param("count") int count,
                          @Param("avg") BigDecimal avg);
}
