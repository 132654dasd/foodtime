package com.foodie.recipe;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import lombok.Getter;
import lombok.Setter;

/**
 * 菜谱。
 *
 * <p>关联集合一律 LAZY，并依靠 {@code hibernate.default_batch_fetch_size=100}
 * 把 N+1 收敛成按 IN 批量查询。
 *
 * <p>刻意<b>不</b>用 {@code join fetch} 同时抓取 steps / ingredients / tags：
 * 那会产生笛卡尔积（3 个集合的乘积行数），比多一条查询慢得多，
 * 而且多个 List 集合同时 fetch 会触发 Hibernate 的 MultipleBagFetchException。
 */
@Entity
@Table(name = "recipe")
@Getter
@Setter
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    /** 为 null 时前端按菜名生成确定性渐变封面。 */
    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    @Column(length = 1000)
    private String description;

    /** 菜系：家常 / 川 / 粤 / 湘 / 鲁 / 苏 / 浙 / 闽 / 西式…… */
    @Column(nullable = false, length = 30)
    private String cuisine;

    @Column(nullable = false, length = 20)
    private DishCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Difficulty difficulty;

    /** 与 difficulty 冗余的排序键，让 ORDER BY 能走索引而不是函数转换。 */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "difficulty_level", nullable = false)
    private Integer difficultyLevel;

    @Column(name = "cook_minutes", nullable = false)
    private Integer cookMinutes = 30;

    @Column(nullable = false)
    private Integer servings = 2;

    /** 每份热量 kcal，允许未知。 */
    private Integer calories;

    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    private Integer ratingCount = 0;

    @Column(name = "view_count", nullable = false)
    private Integer viewCount = 0;

    @Column(name = "favorite_count", nullable = false)
    private Integer favoriteCount = 0;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // -------------------------------------------------------------- 关联

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("stepNo ASC")
    private List<RecipeStep> steps = new ArrayList<>();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortNo ASC")
    private List<RecipeIngredient> ingredients = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "recipe_tag_rel",
            joinColumns = @JoinColumn(name = "recipe_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<RecipeTag> tags = new LinkedHashSet<>();

    // -------------------------------------------------------------- 便捷方法

    public void addStep(RecipeStep step) {
        step.setRecipe(this);
        this.steps.add(step);
    }

    public void addIngredient(RecipeIngredient ingredient) {
        ingredient.setRecipe(this);
        this.ingredients.add(ingredient);
    }

    /** 保持 difficulty 与 difficultyLevel 一致，避免写库时两处不同步。 */
    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
        this.difficultyLevel = difficulty == null ? null : difficulty.level();
    }
}
