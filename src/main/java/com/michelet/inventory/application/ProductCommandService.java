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

    public ProductResult createProduct(CreateProductCommand command) {
        // 1. p_products 저장
        Product product = Product.create(
            command.restaurantId(), command.name(), command.category(),
            command.basePrice(), command.attributes()
        );
        productRepository.save(product);

        // 2. p_product_exhibitions 저장
        ProductExhibition exhibition = ProductExhibition.create(
            product, command.exhibition().startAt(), command.exhibition().endAt()
        );
        productExhibitionRepository.save(exhibition);

        // 3. p_product_options & p_stocks 저장
        command.options().forEach(opt -> {
            ProductOption option = ProductOption.create(product, opt.name(), opt.addPrice());
            productOptionRepository.save(option);

            Stock stock = Stock.create(
                option.getId(), opt.totalQuantity(), opt.dailyLimit(), opt.maxLimit()
            );
            stockRepository.save(stock);
        });

        //TODO 4. Kafka product.created 이벤트 발행 (추후 구현)
        // kafkaProducer.send("product.created", ProductCreatedEvent.from(product));

        return new ProductResult(product.getId());
    }
}
