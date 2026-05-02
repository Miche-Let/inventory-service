//package com.michelet.inventory.application;
//
//import com.michelet.inventory.domain.repository.StockRepository;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//TODO 일일재고복구
//public class StockSchedulerService {
//
//    private final StockRepository stockRepository;
//
//    // "초 분 시 일 월 요일" 순서임
//    // 매일 자정(0시 0분 0초)에 실행하려면: @Scheduled(cron = "0 0 0 * * *")
//    // (테스트용) 1분마다 실행하려면: @Scheduled(cron = "0 * * * * *")
//
//    @Transactional
//    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul") // 한국 시각 기준 매일 자정
//    public void resetDailyStocks() {
//        log.info("일일 재고 초기화 스케줄러 시작...");
//
//        int updatedCount = stockRepository.resetDailyStock();
//
//        log.info("일일 재고 초기화 완료! 업데이트된 상품 수: {}", updatedCount);
//
//        // TODO: 카탈로그 서비스(MongoDB)에도 이 초기화 사실을 알려야해서
//        //  여기서 "전체 재고 초기화 이벤트"를 Kafka로 던져주는 로직을 나중에 추가해야함
//    }
//}
