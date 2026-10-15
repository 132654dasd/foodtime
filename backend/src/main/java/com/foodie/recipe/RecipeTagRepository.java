package com.foodie.recipe;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeTagRepository extends JpaRepository<RecipeTag, Long> {

    Optional<RecipeTag> findByName(String name);

    List<RecipeTag> findByNameIn(Iterable<String> names);
}
