package com.michelet.inventory.application;

import com.michelet.inventory.presentation.dto.ReserveStockRequest;
import com.michelet.inventory.presentation.dto.RestoreStockRequest;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockLockFacade {

    private final RedissonClient redissonClient;
    private final StockCommandService stockCommandService; // 기존 트랜잭션 서비스

    // 1. 재고 선점 (Order 생성 시 호출)
    public void reserveStockWithLock(ReserveStockRequest request) {
        RLock lock = redissonClient.getLock("stock:" + request.optionId());

        try {
            // 락 획득 시도
            // 매개변수: 최대 5초 대기, 락 획득 시 3초간 점유 후 자동 해제(데드락 방지)
            boolean available = lock.tryLock(5, 3, TimeUnit.SECONDS);
            if (!available) {
                log.error("[StockLockFacade] 재고 선점 락 획득 실패 - OptionId: {}", request.optionId());
                throw new IllegalStateException("재고 선점 처리 중 락을 획득하지 못했습니다.");
            }

            // 락 획득 성공 시 실제 비즈니스 로직(트랜잭션) 실행
            stockCommandService.reserveStock(request);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("재고 선점 락 대기 중 인터럽트 발생");
        } finally {
            if (lock != null && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // 2. 재고 복구 (Order 실패/취소 시 보상 트랜잭션으로 호출)
    public void restoreStockWithLock(RestoreStockRequest request) {
        RLock lock = redissonClient.getLock("stock:" + request.optionId());

        try {
            // 복구 로직은 선점보다 더 중요하므로 대기 시간을 넉넉히(일단 10초) 줌
            boolean available = lock.tryLock(10, 5, TimeUnit.SECONDS);
            if (!available) {
                log.error("[CRITICAL] 재고 복구 락 획득 실패 (수동 복구 필요) - OptionId: {}", request.optionId());
                throw new IllegalStateException("재고 복구 처리 중 락을 획득하지 못했습니다.");
            }

            // 락 획득 성공 시 복구 비즈니스 로직 실행
            stockCommandService.restoreStock(request);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("재고 복구 락 대기 중 인터럽트 발생");
        } finally {
            if (lock != null && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
