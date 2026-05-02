package com.michelet.inventory.domain.model.vo;

public record Quantity(Integer value) {
    // 컴팩트 생성자 - 객체를 진짜 생성할 때도 내부적으로 validate()를 호출하여 중복 제거
    public Quantity {
        validate(value);
    }

    // 정적 검증 메서드: 객체 생성 없이 값만 빠르게 검사할 때 사용
    public static void validate(Integer value) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException("수량은 0개 이상이어야 합니다.");
        }
    }
}
