package com.michelet.inventory.application;

import com.michelet.inventory.application.dto.CreateProductCommand;
import com.michelet.inventory.application.dto.ProductResult;
import com.michelet.inventory.domain.model.Product;
import com.michelet.inventory.domain.model.ProductExhibition;
import com.michelet.inventory.domain.model.ProductOption;
import com.michelet.inventory.domain.model.Stock;
import com.michelet.inventory.domain.repository.ProductExhibitionRepository;
import com.michelet.inventory.domain.repository.ProductOptionRepository;
import com.michelet.inventory.domain.repository.ProductRepository;
import com.michelet.inventory.domain.repository.StockRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ProductCommandService {

    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;
    private final ProductExhibitionRepository productExhibitionRepository;
    private final StockRepository stockRepository;

    @Transactional(readOnly = true)
    public String checkHealth() {
        return "Inventory Command Service is Healthy";
    }

    @Transactional
    public ProductResult createProduct(CreateProductCommand command) {
        // 1. p_products 저장
        Product product = Product.create(
            command.restaurantId(), command.name(), command.category(),
            command.basePrice(), command.attributes()
        );
        productRepository.save(product);

        // 2. p_product_exhibitions 저장
        productExhibitionRepository.save(ProductExhibition.create(
            product, command.exhibition().startAt(), command.exhibition().endAt()
        ));

        // 3. 옵션(Options) 리스트 생성 및 일괄 저장
        List<ProductOption> options = command.options().stream()
            .map(opt -> ProductOption.create(product, opt.name(), opt.addPrice()))
            .toList();

        // saveAll을 호출하면 ID가 채워진 저장된 리스트가 반환됨
        List<ProductOption> savedOptions = productOptionRepository.saveAll(options);

        // 4. 재고(Stocks) 리스트 생성 및 일괄 저장
        // savedOptions와 command.options()의 순서가 동일하므로 index를 활용하거나 매칭함
        List<Stock> stocks = new ArrayList<>();
        for (int i = 0; i < savedOptions.size(); i++) {
            var optCommand = command.options().get(i);
            var savedOption = savedOptions.get(i);

            stocks.add(Stock.create(
                savedOption.getId(),
                optCommand.totalQuantity(),
                optCommand.dailyLimit(),
                optCommand.maxLimit()
            ));
        }
        stockRepository.saveAll(stocks);

        //TODO 4. Kafka product.created 이벤트 발행 (추후 구현)
        // kafkaProducer.send("product.created", ProductCreatedEvent.from(product));

        return new ProductResult(product.getId());
    }
}
