import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.Set;


public class CampaignExample {

    public record Discount(String name, BigDecimal amount) {}

    record CartItem(
            String productName,
            int quantity,
            BigDecimal unitPrice
    ){
        public BigDecimal totalPrice() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    public record Cart(List<CartItem> items){
        public BigDecimal totalPrice(){
            return items().stream()
                    .map(CartItem::totalPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    public interface DiscountStrategy<T extends Campaign> {
        boolean isApplicable(Cart cart, T campaign);
        Discount apply(Cart cart, T campaign);
        String name();

        Class<T> getCampaignType();
    }
    public interface Campaign{

    }

    public record PercentageCampaign(String name, BigDecimal rate, BigDecimal minOrderTotal) implements Campaign{

    }

    public static class PercentageStrategy implements DiscountStrategy<PercentageCampaign> {

        @Override
        public boolean isApplicable(Cart cart, PercentageCampaign campaign) {
            return cart.totalPrice().compareTo(campaign.minOrderTotal()) > 0;
        }

        @Override
        public Discount apply(Cart cart, PercentageCampaign campaign) {
            var discountAmount = cart.totalPrice()
                    .multiply(campaign.rate)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            return new Discount(campaign.name, discountAmount);
        }

        @Override
        public String name() {
            return "percentage";
        }

        @Override
        public Class<PercentageCampaign> getCampaignType() {
            return PercentageCampaign.class;
        }
    }


    static class DiscountService {
        private final List<DiscountStrategy<?>> strategies;

        DiscountService(List<DiscountStrategy<?>> strategies) {
            this.strategies = strategies;
        }

        public List<Discount> evaluate(Cart cart, Set<Campaign> campaigns) {
            return campaigns.stream()
                    .flatMap(campaign -> strategies.stream()
                            .filter(strategy -> strategy.getCampaignType().isInstance(campaign))
                            .map(strategy -> applyIfApplicable(strategy, cart, campaign))
                            .flatMap(Optional::stream)
                    )
                    .toList();
        }

        @SuppressWarnings("unchecked")
        private <T extends Campaign> Optional<Discount> applyIfApplicable(DiscountStrategy<T> strategy, Cart cart, Campaign campaign) {
            T casted = (T) campaign;
            if (strategy.isApplicable(cart, casted)) {
                return Optional.of(strategy.apply(cart, casted));
            }
            return Optional.empty();
        }
    }

    public static void main(String[] args) {
        Cart cart = new Cart(List.of(new CartItem("Laptop", 1, BigDecimal.valueOf(250))));
        Campaign campaign = new PercentageCampaign("Summer Sale", BigDecimal.valueOf(10), BigDecimal.valueOf(200));
        DiscountService service = new DiscountService(List.of(new PercentageStrategy()));
        List<Discount> discounts = service.evaluate(cart, Set.of(campaign));
        System.out.println(discounts);
    }
}
