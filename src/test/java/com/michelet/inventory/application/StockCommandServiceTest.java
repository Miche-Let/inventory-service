package com.michelet.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.michelet.inventory.application.dto.StockReservedEvent;
import com.michelet.inventory.application.dto.StockRestoredEvent;
import com.michelet.inventory.domain.exception.ConcurrencyFailureException;
import com.michelet.inventory.domain.exception.MaxLimitExceededException;
import com.michelet.inventory.domain.exception.OutOfStockException;
import com.michelet.inventory.domain.exception.SoldOutException;
import com.michelet.inventory.domain.exception.StockNotFoundException;
import com.michelet.inventory.domain.model.Stock;
import com.michelet.inventory.domain.repository.StockRepository;
import com.michelet.inventory.presentation.dto.ReserveStockRequest;
import com.michelet.inventory.presentation.dto.RestoreStockRequest;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class StockCommandServiceTest {

    @InjectMocks
    private StockCommandService stockCommandService;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private TransactionTemplate transactionTemplate;

    // 카프카로 전송된 이벤트를 낚아채서 내부 값을 검증하기 위한 Captor
    @Captor
    private ArgumentCaptor<StockReservedEvent> eventCaptor;

    @Captor
    private ArgumentCaptor<StockRestoredEvent> restoredEventCaptor;

    @BeforeEach
    void setUp() {
        given(transactionTemplate.execute(any())).willAnswer(invocation -> {
            TransactionCallback<?> action = invocation.getArgument(0);
            return action.doInTransaction(null);
        });
        ReflectionTestUtils.setField(stockCommandService, "maxRetryCount", 3);
        ReflectionTestUtils.setField(stockCommandService, "topicStockReserved", "stock.reserved");
        ReflectionTestUtils.setField(stockCommandService, "topicStockRestored", "stock.restored");
    }

    @Test
    @DisplayName("성공: 재고가 충분하면 차감되고 Kafka 이벤트가 발행된다.")
    void reserveStock_Success() {
        // given
        UUID optionId = UUID.randomUUID();
        Stock stock = Stock.create(optionId, 100, 50, 10);
        ReserveStockRequest request = new ReserveStockRequest(optionId, 2, null);

        given(stockRepository.findById(optionId)).willReturn(Optional.of(stock));
        given(kafkaTemplate.send(eq("stock.reserved"), eq(optionId.toString()), any(StockReservedEvent.class)))
            .willReturn(CompletableFuture.completedFuture(null));

        // when
        stockCommandService.reserveStockWithRetry(request);

        // then
        verify(stockRepository, times(1)).save(any(Stock.class));
        // 캡처를 통해 Kafka로 넘어간 페이로드(이벤트 객체)의 상세 데이터 검증
        verify(kafkaTemplate, times(1)).send(eq("stock.reserved"), eq(optionId.toString()), eventCaptor.capture());
        StockReservedEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.optionId()).isEqualTo(optionId);
        assertThat(capturedEvent.totalQuantity()).isEqualTo(98); // 100 - 2
        assertThat(capturedEvent.currentDailyStock()).isEqualTo(48); // 50 - 2
    }

    @Test
    @DisplayName("실패: 요청 수량이 현재 일일 재고보다 많으면 예외가 발생한다.")
    void reserveStock_Fail_NotEnoughDailyStock() {
        // given
        UUID optionId = UUID.randomUUID();
        Stock stock = Stock.create(optionId, 100, 1, 10); // 일일 재고 1개
        ReserveStockRequest request = new ReserveStockRequest(optionId, 2, null); // 2개 요청

        given(stockRepository.findById(optionId)).willReturn(Optional.of(stock));

        // when & then
        assertThatThrownBy(() -> stockCommandService.reserveStockWithRetry(request))
            .isInstanceOf(OutOfStockException.class);

        // 예외가 터졌으므로 Kafka 전송은 절대 일어나지 않아야 함
        verifyNoInteractions(kafkaTemplate);
    }

    // 전체 재고 부족 예외 테스트
    @Test
    @DisplayName("실패: 요청 수량이 전체 남은 재고보다 많으면 예외가 발생한다.")
    void reserveStock_Fail_NotEnoughTotalQuantity() {
        // given
        UUID optionId = UUID.randomUUID();
        Stock stock = Stock.create(optionId, 1, 50, 10); // 전체 재고 1개
        ReserveStockRequest request = new ReserveStockRequest(optionId, 2, null); // 2개 요청

        given(stockRepository.findById(optionId)).willReturn(Optional.of(stock));

        // when & then
        assertThatThrownBy(() -> stockCommandService.reserveStockWithRetry(request))
            .isInstanceOf(SoldOutException.class);

        verifyNoInteractions(kafkaTemplate);
    }

    // 1인당 구매 한도 초과 예외 테스트
    @Test
    @DisplayName("실패: 요청 수량이 1인당 구매 한도를 초과하면 예외가 발생한다.")
    void reserveStock_Fail_MaxLimitExceeded() {
        // given
        UUID optionId = UUID.randomUUID();
        Stock stock = Stock.create(optionId, 100, 50, 1); // 1인당 1개 제한
        ReserveStockRequest request = new ReserveStockRequest(optionId, 2, null); //2개 요청

        given(stockRepository.findById(optionId)).willReturn(Optional.of(stock));

        // when & then
        assertThatThrownBy(() -> stockCommandService.reserveStockWithRetry(request))
            .isInstanceOf(MaxLimitExceededException.class);

        verifyNoInteractions(kafkaTemplate);
    }

    // N번 시도 후 성공하는 낙관적 락 재시도 테스트
    @Test
    @DisplayName("재시도 성공: 낙관적 락 충돌이 발생해도 3회 이내면 재시도하여 성공한다.")
    void reserveStock_Retry_Success() {
        // given
        UUID optionId = UUID.randomUUID();
        ReserveStockRequest request = new ReserveStockRequest(optionId, 2, null);

        // DB에서 최신 데이터를 다시 읽어오는 동작을 시뮬레이션 (매 호출마다 새로운 Stock 객체 반환)
        given(stockRepository.findById(optionId)).willAnswer(invocation ->
            Optional.of(Stock.create(optionId, 100, 50, 10))
        );

        // 첫 번째, 두 번째는 예외 발생시키고 세 번째에 정상 통과
        given(stockRepository.save(any(Stock.class)))
            .willThrow(new ObjectOptimisticLockingFailureException(Stock.class.getName(), optionId))
            .willThrow(new ObjectOptimisticLockingFailureException(Stock.class.getName(), optionId))
            .willReturn(null);

        given(kafkaTemplate.send(anyString(), anyString(), any()))
            .willReturn(CompletableFuture.completedFuture(null));

        // when
        stockCommandService.reserveStockWithRetry(request);

        // then - findById가 매 재시도마다 호출되었는지(총 3회) 검증
        verify(stockRepository, times(3)).findById(optionId);
        verify(stockRepository, times(3)).save(any(Stock.class));

        // 카프카 페이로드 내부 필드 검증
        verify(kafkaTemplate, times(1)).send(eq("stock.reserved"), eq(optionId.toString()), eventCaptor.capture());
        StockReservedEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.optionId()).isEqualTo(optionId);
        assertThat(capturedEvent.totalQuantity()).isEqualTo(98);
        assertThat(capturedEvent.currentDailyStock()).isEqualTo(48);
    }

    // 최대 재시도 횟수 초과 실패 테스트
    @Test
    @DisplayName("재시도 실패: 3회를 초과하여 낙관적 락 충돌이 발생하면 예외를 던진다.")
    void reserveStock_Retry_Fail_MaxAttempts() {
        // given
        UUID optionId = UUID.randomUUID();
        ReserveStockRequest request = new ReserveStockRequest(optionId, 2, null);

        given(stockRepository.findById(optionId)).willAnswer(invocation ->
            Optional.of(Stock.create(optionId, 100, 50, 10))
        );

        // 항상 충돌 발생
        given(stockRepository.save(any(Stock.class)))
            .willThrow(new ObjectOptimisticLockingFailureException(Stock.class.getName(), optionId));

        // when & then: 3번 시도 후 ConcurrencyFailureException
        assertThatThrownBy(() -> stockCommandService.reserveStockWithRetry(request))
            .isInstanceOf(ConcurrencyFailureException.class);

        verify(stockRepository, times(3)).findById(optionId);
        verify(stockRepository, times(3)).save(any(Stock.class)); // 정확히 3번 시도됨
        verifyNoInteractions(kafkaTemplate); // 실패했으므로 카프카 메시지 발행 안 됨
    }

    @Test
    @DisplayName("성공: 재고 복구 경로 정상 동작 및 Kafka 발행 테스트")
    void restoreStock_Success() {
        UUID optionId = UUID.randomUUID();
        Stock stock = Stock.create(optionId, 100, 50, 10);
        RestoreStockRequest request = new RestoreStockRequest(optionId, 2, null);

        given(stockRepository.findById(optionId)).willReturn(Optional.of(stock));
        given(kafkaTemplate.send(eq("stock.restored"), eq(optionId.toString()), any(StockRestoredEvent.class)))
            .willReturn(CompletableFuture.completedFuture(null));

        stockCommandService.restoreStockWithRetry(request);

        verify(stockRepository, times(1)).save(any(Stock.class));
        verify(kafkaTemplate, times(1)).send(eq("stock.restored"), eq(optionId.toString()),
            restoredEventCaptor.capture());

        StockRestoredEvent event = restoredEventCaptor.getValue();
        assertThat(event.optionId()).isEqualTo(optionId);
        assertThat(event.totalQuantity()).isEqualTo(102);
    }

    @Test
    @DisplayName("재시도 성공: 복구 시 낙관적 락 충돌이 발생해도 재시도하여 성공한다.")
    void restoreStock_Retry_Success() {
        UUID optionId = UUID.randomUUID();
        RestoreStockRequest request = new RestoreStockRequest(optionId, 2, null);

        given(stockRepository.findById(optionId)).willAnswer(invocation ->
            Optional.of(Stock.create(optionId, 100, 50, 10))
        );

        given(stockRepository.save(any(Stock.class)))
            .willThrow(new ObjectOptimisticLockingFailureException(Stock.class.getName(), optionId))
            .willReturn(null); // 두 번째 시도 성공

        given(kafkaTemplate.send(anyString(), anyString(), any()))
            .willReturn(CompletableFuture.completedFuture(null));

        stockCommandService.restoreStockWithRetry(request);

        verify(stockRepository, times(2)).findById(optionId);
        verify(stockRepository, times(2)).save(any(Stock.class));
    }

    @Test
    @DisplayName("실패: 재고 복구 시 존재하지 않는 옵션 예외")
    void restoreStock_Fail_NotFound() {
        UUID optionId = UUID.randomUUID();
        RestoreStockRequest request = new RestoreStockRequest(optionId, 5, null);

        given(stockRepository.findById(optionId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> stockCommandService.restoreStockWithRetry(request))
            .isInstanceOf(StockNotFoundException.class);

        verifyNoInteractions(kafkaTemplate);
    }
}
