package swap.model;

public record Seller(String multiaddr, double minQuantity, double maxQuantity, double price) {

    @Override
    public String toString() {
        return multiaddr() + ", " + price() + ", " + maxQuantity() + ", " + minQuantity();
    }

    public double getPrice() {
        return price;
    }
}