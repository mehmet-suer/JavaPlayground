import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;


public class CampaignExample {

    public enum CampaignType {PERCENTAGE}

    public record Discount(String name, BigDecimal amount) {
        public Discount {
            amount = amount.setScale(2, RoundingMode.HALF_UP);
        }
    }

    public record CartItem(
            String productName,
            int quantity,
            BigDecimal unitPrice) {

        public CartItem {
            validate(productName, quantity, unitPrice);
        }

        private static void validate(String productName, int quantity, BigDecimal unitPrice) {
            if (productName == null || productName.isBlank())
                throw new IllegalArgumentException("productName blank");
            if (quantity <= 0)
                throw new IllegalArgumentException("quantity <= 0: " + quantity);
            if (unitPrice == null || unitPrice.signum() < 0)
                throw new IllegalArgumentException("unitPrice < 0: " + unitPrice);
        }

        public BigDecimal totalPrice() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    public static class Cart {
        private final List<CartItem> items;

        public Cart(List<CartItem> items) {
            this.items = List.copyOf(items);
        }

        public List<CartItem> items() {
            return items;
        }

        public BigDecimal totalPrice() {
            return items.stream()
                    .map(CartItem::totalPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    public interface DiscountStrategy<T extends Campaign> {
        boolean isApplicable(Cart cart, T campaign);

        Discount apply(Cart cart, T campaign);

        String name();

        CampaignType type();

        Class<T> modelClass();

        default Optional<Discount> tryApply(Cart cart, Campaign c) {
            var modelClass = modelClass();
            if (!modelClass.isInstance(c)) {
                throw new IllegalStateException("Strategy %s expected %s but got %s"
                        .formatted(name(), modelClass.getSimpleName(), c.getClass().getSimpleName()));
            }

            T typed = modelClass.cast(c);
            return isApplicable(cart, typed) ? Optional.of(apply(cart, typed)) : Optional.empty();
        }
    }

    public sealed interface Campaign permits PercentageCampaign {
        CampaignType type();
    }

    public record PercentageCampaign(String name, BigDecimal rate, BigDecimal minOrderTotal) implements Campaign {

        public PercentageCampaign {
            validate(name, rate, minOrderTotal);
        }

        private void validate(String name, BigDecimal rate, BigDecimal minOrderTotal) {
            if (rate == null || rate.signum() < 0) {
                throw new IllegalArgumentException("rate < 0");
            }
            if (minOrderTotal == null || minOrderTotal.signum() < 0) {
                throw new IllegalArgumentException("minOrderTotal < 0");
            }
            if (name == null || name.isBlank()){
                throw new IllegalArgumentException("blank name");
            }
        }

        @Override
        public CampaignType type() {
            return CampaignType.PERCENTAGE;
        }
    }

    public static class PercentageDiscountStrategy implements DiscountStrategy<PercentageCampaign> {
        private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

        @Override
        public CampaignType type() {
            return CampaignType.PERCENTAGE;
        }

        @Override
        public Class<PercentageCampaign> modelClass() {
            return PercentageCampaign.class;
        }

        @Override
        public boolean isApplicable(Cart cart, PercentageCampaign campaign) {
            return cart.totalPrice().compareTo(campaign.minOrderTotal()) > 0;
        }

        @Override
        public Discount apply(Cart cart, PercentageCampaign campaign) {
            var total = cart.totalPrice();
            var discountAmount = calculateDiscount(campaign, total);
            if (discountAmount.compareTo(total) > 0) {
                discountAmount = total;
            }
            return new Discount(campaign.name(), discountAmount);
        }

        private BigDecimal calculateDiscount(PercentageCampaign campaign, BigDecimal total) {
            return total.multiply(campaign.rate()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        }

        @Override
        public String name() {
            return "percentage";
        }
    }


    static class DiscountService {
        private final Map<CampaignType, DiscountStrategy<? extends Campaign>> byType;

        public DiscountService(List<DiscountStrategy<? extends Campaign>> strategies) {
            this.byType = strategies.stream()
                    .collect(Collectors.toMap(
                            DiscountStrategy::type,
                            Function.identity(),
                            (a, b) -> {
                                throw new IllegalStateException("Duplicate strategy for type: " + a.type());
                            }
                    ));
        }

        public List<Discount> evaluate(Cart cart, Set<Campaign> campaigns) {
            return campaigns.stream()
                    .map(c -> applyIfApplicable(cart, c))
                    .flatMap(Optional::stream)
                    .toList();
        }

        private Optional<Discount> applyIfApplicable(Cart cart, Campaign campaign) {
            var strategy = getDiscountStrategy(campaign.type());
            return strategy.tryApply(cart, campaign);
        }

        private DiscountStrategy<?> getDiscountStrategy(CampaignType campaignType) {
            return Optional.ofNullable(byType.get(campaignType)).orElseThrow(() -> new IllegalStateException("Strategy for Campaign Type " + campaignType + " not found"));
        }
    }

    public static void main(String[] args) {
        Cart cart = new Cart(List.of(new CartItem("Laptop", 1, BigDecimal.valueOf(250))));
        Campaign campaign = new PercentageCampaign("Summer Sale", BigDecimal.valueOf(10), BigDecimal.valueOf(200));
        DiscountService service = new DiscountService(List.of(new PercentageDiscountStrategy()));
        List<Discount> discounts = service.evaluate(cart, Set.of(campaign));
        System.out.println(discounts);
    }
}
