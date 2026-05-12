package com.michelet.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.michelet.inventory.application.dto.StockReservedEvent;
import com.michelet.inventory.application.dto.StockRestoredEvent;
import com.michelet.inventory.domain.exception.MaxLimitExceededException;
import com.michelet.inventory.domain.exception.OutOfStockException;
import com.michelet.inventory.domain.exception.SoldOutException;
import com.michelet.inventory.domain.exception.StockNotFoundException;
import com.michelet.inventory.domain.model.Stock;
import com.michelet.inventory.domain.repository.ProductOptionRepository;
import com.michelet.inventory.domain.repository.ProductRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StockCommandServiceTest {

    @InjectMocks
    private StockCommandService stockCommandService;

    @Mock
    private StockRepository stockRepository;

    // SOLDOUT 자동 전이 처리를 위한 Repository Mock 추가
    @Mock
    private ProductOptionRepository productOptionRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    // 카프카로 전송된 이벤트를 낚아채서 내부 값을 검증하기 위한 Captor
    @Captor
    private ArgumentCaptor<StockReservedEvent> eventCaptor;

    @Captor
    private ArgumentCaptor<StockRestoredEvent> restoredEventCaptor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(stockCommandService, "topicStockReserved", "stock.reserved");
        ReflectionTestUtils.setField(stockCommandService, "topicStockRestored", "stock.restored");
        ReflectionTestUtils.setField(stockCommandService, "topicStatusChanged", "product.status-changed");
    }

    @Test
    @DisplayName("성공: 재고가 충분하면 차감되고 Kafka 이벤트가 발행된다.")
    void reserveStock_Success() {
        // given
        UUID optionId = UUID.randomUUID();
        Stock stock = Stock.create(optionId, 100, 50, 10);
        ReserveStockRequest request = new ReserveStockRequest(optionId, 2, null);

        given(stockRepository.findById(optionId)).willReturn(Optional.of(stock));
        given(kafkaTemplate.send(eq("stock.reserved"), eq(optionId.toString()), any()))
            .willReturn(CompletableFuture.completedFuture(null));

        // when
        stockCommandService.reserveStock(request);

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
        assertThatThrownBy(() -> stockCommandService.reserveStock(request))
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
        assertThatThrownBy(() -> stockCommandService.reserveStock(request))
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
        assertThatThrownBy(() -> stockCommandService.reserveStock(request))
            .isInstanceOf(MaxLimitExceededException.class);

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("성공: 재고 복구 경로 정상 동작 및 Kafka 발행 테스트")
    void restoreStock_Success() {
        UUID optionId = UUID.randomUUID();
        // 1. 초기 재고 100개, 일일 재고 50개 생성
        Stock stock = Stock.create(optionId, 100, 50, 10);

        // 2. 소비된 상태를 시뮬레이션하기 위해 미리 2개를 차감 (total: 98, daily: 48)
        stock.reserve(2);

        // 3. 다시 2개를 복구해 달라는 요청
        RestoreStockRequest request = new RestoreStockRequest(optionId, 2, null);

        given(stockRepository.findById(optionId)).willReturn(Optional.of(stock));
        given(kafkaTemplate.send(eq("stock.restored"), eq(optionId.toString()), any()))
            .willReturn(CompletableFuture.completedFuture(null));

        // when
        stockCommandService.restoreStock(request);

        // then
        verify(stockRepository, times(1)).save(any(Stock.class));
        verify(kafkaTemplate, times(1)).send(eq("stock.restored"), eq(optionId.toString()),
            restoredEventCaptor.capture());

        StockRestoredEvent event = restoredEventCaptor.getValue();
        assertThat(event.optionId()).isEqualTo(optionId);

        // totalQuantity와 currentDailyStock이 모두 100과 50으로 정상 복구되었는지 검증
        assertThat(event.totalQuantity()).isEqualTo(100);
        assertThat(event.currentDailyStock()).isEqualTo(50);
    }

    @Test
    @DisplayName("실패: 재고 복구 시 존재하지 않는 옵션 예외")
    void restoreStock_Fail_NotFound() {
        UUID optionId = UUID.randomUUID();
        RestoreStockRequest request = new RestoreStockRequest(optionId, 5, null);

        given(stockRepository.findById(optionId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> stockCommandService.restoreStock(request))
            .isInstanceOf(StockNotFoundException.class);

        verifyNoInteractions(kafkaTemplate);
    }
}
