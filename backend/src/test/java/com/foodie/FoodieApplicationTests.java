package com.foodie;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 冒烟测试：验证 Spring 容器能正常装配。
 *
 * <p>使用 {@code test} profile（H2），因此不依赖本机 MySQL 是否在跑，
 * CI 上也能独立通过。真实 MySQL 的连通性由 {@code FoodieMySqlIT} 单独覆盖。
 */
@SpringBootTest
@ActiveProfiles("test")
class FoodieApplicationTests {

    @Test
    void contextLoads() {
    }
}
