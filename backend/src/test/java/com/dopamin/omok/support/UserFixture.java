package com.dopamin.omok.support;

import com.dopamin.omok.user.domain.User;

import java.lang.reflect.Field;

public class UserFixture {

    public static User create(Long id, String email, String nickname) {
        User user = User.createLocalUser(email, "encodedPassword1!", nickname);
        setField(user, "id", id);
        return user;
    }

    public static User create(Long id) {
        return create(id, "user" + id + "@test.com", "nick" + id);
    }

    public static User create() {
        return create(1L, "test@test.com", "testNick");
    }

    public static User createWithEmail(Long id, String email) {
        return create(id, email, "nick" + id);
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }
}
