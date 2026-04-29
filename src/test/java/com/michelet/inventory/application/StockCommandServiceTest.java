package com.michelet.inventory.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.michelet.inventory.application.dto.StockReservedEvent;
import com.michelet.inventory.domain.model.Stock;
import com.michelet.inventory.domain.repository.StockRepository;
import com.michelet.inventory.presentation.dto.ReserveStockRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class StockCommandServiceTest {

    @InjectMocks
    private StockCommandService stockCommandService;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    @DisplayName("성공: 재고가 충분하면 차감되고 Kafka 이벤트가 발행된다.")
    void reserveStock_Success() {
        // given
        UUID optionId = UUID.randomUUID();
        Stock stock = Stock.create(optionId, 100, 50, 10);
        ReserveStockRequest request = new ReserveStockRequest(optionId, 2);

        given(stockRepository.findById(optionId)).willReturn(Optional.of(stock));

        // when
        stockCommandService.reserveStockWithRetry(request);

        // then
        verify(stockRepository, times(1)).save(any(Stock.class));
        verify(kafkaTemplate, times(1)).send(eq("stock.reserved"), eq(optionId.toString()),
            any(StockReservedEvent.class));
    }

    @Test
    @DisplayName("실패: 요청 수량이 현재 일일 재고보다 많으면 예외가 발생한다.")
    void reserveStock_Fail_NotEnoughDailyStock() {
        // given
        UUID optionId = UUID.randomUUID();
        Stock stock = Stock.create(optionId, 100, 1, 10); // 일일 재고 1개
        ReserveStockRequest request = new ReserveStockRequest(optionId, 2); // 2개 요청

        given(stockRepository.findById(optionId)).willReturn(Optional.of(stock));

        // when & then
        assertThatThrownBy(() -> stockCommandService.reserveStockWithRetry(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("일일 판매 한도 초과"); // Stock.java의 실제 메시지랑 맞춤

        // 예외가 터졌으므로 Repository와 Kafka는 아예 건드리지 않아야 함
        verifyNoInteractions(kafkaTemplate);
    }
}
