package com.foodie.recipe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * 菜谱标签。
 *
 * <p>除了做分类展示，还承担「忌口辅助信号」的职责：
 * 像「花生」「坚果」「乳制品」「麸质」「辛辣」这些过敏原，
 * 未必能从食材名直接看出来，用标签兜一层更稳。
 */
@Entity
@Table(name = "recipe_tag")
@Getter
@Setter
public class RecipeTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String name;
}
