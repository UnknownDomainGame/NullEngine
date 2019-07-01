package nullengine.item;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.Consumer;

public final class ItemStack {

    public static final ItemStack EMPTY = new ItemStack();

    private final Item item;

    private int amount;

    public ItemStack(@Nonnull Item item) {
        this(item, 1);
    }

    public ItemStack(@Nonnull Item item, int amount) {
        this.item = Objects.requireNonNull(item);
        this.amount = amount;
    }

    private ItemStack() {
        this.item = null;
        this.amount = 0;
    }

    @Nullable
    public Item getItem() {
        return item;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public boolean isEmpty() {
        return item == null || amount <= 0;
    }

    public void ifEmpty(Consumer<ItemStack> consumer) {
        if (isEmpty()) {
            consumer.accept(this);
        }
    }

    public void ifNonEmpty(Consumer<ItemStack> consumer) {
        if (!isEmpty()) {
            consumer.accept(this);
        }
    }

    //amount can be less than 0
    public void changeAmount(int change){
        amount+=change;
    }
}
