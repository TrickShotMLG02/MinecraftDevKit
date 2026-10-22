package com.trickshotmlg.devkit.bukkit.gui;

import net.kyori.adventure.text.Component;
import com.trickshotmlg.devkit.text.TextRef;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.Event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/** Builder for a menu whose content is automatically mapped across a set of slots. */
public final class PagedGui<T> {
    private PagedGui() { }
    public static <T> Builder<T> chest(int rows) { return new Builder<>(rows); }

    public static final class Builder<T> {
        private final int rows;
        private Object title = "";
        private List<Integer> contentSlots;
        private Function<GuiRenderContext, ? extends List<T>> items;
        private BiFunction<GuiRenderContext, T, GuiComponent> renderer;
        private Integer previousSlot, indicatorSlot, nextSlot, emptySlot;
        private BiFunction<GuiRenderContext, PageInfo, ItemStack> indicator;
        private GuiItemTemplate previous, next, disabled, empty;
        private final List<GuiRefreshBinding<?>> refreshBindings = new ArrayList<>();
        private Builder(int rows) {
            if (rows < 1 || rows > 6) throw new IllegalArgumentException("Chest rows must be between 1 and 6");
            this.rows = rows;
        }
        public Builder<T> title(String value) { title = Objects.requireNonNull(value, "title"); return this; }
        public Builder<T> title(Component value) { title = Objects.requireNonNull(value, "title"); return this; }
        public Builder<T> title(TextRef value) { title = Objects.requireNonNull(value, "title"); return this; }
        public Builder<T> contentSlots(List<Integer> slots) { contentSlots = List.copyOf(Objects.requireNonNull(slots, "slots")); return this; }
        public Builder<T> items(Function<GuiRenderContext, ? extends List<T>> source) { items = Objects.requireNonNull(source, "items"); return this; }
        public Builder<T> render(BiFunction<GuiRenderContext, T, GuiComponent> renderer) { this.renderer = Objects.requireNonNull(renderer, "render"); return this; }
        public Builder<T> previousPage(int slot) { previousSlot = slot; return this; }
        public Builder<T> nextPage(int slot) { nextSlot = slot; return this; }
        public Builder<T> pageIndicator(int slot) { indicatorSlot = slot; return this; }
        public Builder<T> pageIndicator(int slot, Function<PageInfo, ItemStack> renderer) {
            Objects.requireNonNull(renderer, "renderer");
            indicatorSlot = slot;
            indicator = (context, page) -> renderer.apply(page);
            return this;
        }
        public Builder<T> pageIndicator(int slot, BiFunction<GuiRenderContext, PageInfo, ItemStack> renderer) {
            indicatorSlot = slot;
            indicator = Objects.requireNonNull(renderer, "renderer");
            return this;
        }
        public Builder<T> navigationItems(ItemStack previous, ItemStack next, ItemStack disabled) {
            this.previous = template(previous); this.next = template(next); this.disabled = template(disabled); return this;
        }
        /** Sets previous/next/disabled items using per-viewer render-time templates. */
        public Builder<T> navigationTemplates(GuiItemTemplate previous, GuiItemTemplate next, GuiItemTemplate disabled) {
            this.previous = previous; this.next = next; this.disabled = disabled; return this;
        }
        public Builder<T> emptyState(int slot, ItemStack item) { emptySlot = slot; empty = template(item); return this; }
        /** Sets an empty-state item rendered for the current viewer. */
        public Builder<T> emptyStateTemplate(int slot, GuiItemTemplate item) {
            emptySlot = slot; empty = Objects.requireNonNull(item, "item"); return this;
        }
        public <E extends Event> Builder<T> refreshOn(Class<E> eventType, Predicate<E> filter, RefreshScope scope) {
            refreshBindings.add(new GuiRefreshBinding<>(eventType, filter, scope)); return this;
        }
        public <E extends Event> Builder<T> refreshOn(Class<E> eventType) { return refreshOn(eventType, ignored -> true, RefreshScope.FULL); }

        public GuiMenu build() {
            if (items == null || renderer == null) throw new IllegalStateException("Paged GUI requires an item source and renderer");
            List<Integer> slots = contentSlots == null ? defaultContentSlots() : contentSlots;
            if (slots.isEmpty()) throw new IllegalArgumentException("Paged GUI needs at least one content slot");
            if (new LinkedHashSet<>(slots).size() != slots.size()) throw new IllegalArgumentException("Content slots cannot contain duplicates");
            for (int slot : slots) checkSlot(slot);
            LinkedHashSet<Integer> reserved = new LinkedHashSet<>(slots);
            for (Integer slot : new Integer[]{previousSlot, indicatorSlot, nextSlot, emptySlot}) {
                if (slot == null) continue;
                checkSlot(slot);
                if (!reserved.add(slot)) throw new IllegalArgumentException("Pagination controls overlap at slot " + slot);
            }
            List<Integer> immutableSlots = List.copyOf(slots);
            GuiStateKey<Integer> pageKey = GuiStateKey.of("paged.current-page", 1);
            GuiMenu.Builder menu = GuiMenu.chest(rows);
            menu.contentRefreshSlots(reserved);
            refreshBindings.forEach(menu::refreshBinding);
            if (title instanceof String value) menu.title(value);
            else if (title instanceof Component value) menu.title(value);
            else menu.title((TextRef) title);

            for (int contentIndex = 0; contentIndex < immutableSlots.size(); contentIndex++) {
                final int index = contentIndex;
                menu.component(immutableSlots.get(contentIndex), new ContentComponent<>(this, pageKey, immutableSlots, index));
            }
            if (emptySlot != null) menu.component(emptySlot, new EmptyComponent<>(this, pageKey, immutableSlots));
            if (previousSlot != null) menu.component(previousSlot, new PageControl<>(this, pageKey, immutableSlots, false));
            if (nextSlot != null) menu.component(nextSlot, new PageControl<>(this, pageKey, immutableSlots, true));
            if (indicatorSlot != null) menu.component(indicatorSlot, new Indicator<>(this, pageKey, immutableSlots));
            return menu.build();
        }

        private Snapshot<T> snapshot(GuiRenderContext context, GuiStateKey<Integer> pageKey, List<Integer> slots) {
            return context.session().renderValue(this, () -> {
                List<T> provided = Objects.requireNonNull(items.apply(context), "Paged item source returned null");
                List<T> copy = List.copyOf(provided);
                int requested = context.session().get(pageKey);
                PageInfo page = PageInfo.of(requested, copy.size(), slots.size());
                if (page.currentPage() != requested) context.session().setRenderState(pageKey, page.currentPage());
                return new Snapshot<>(copy, page);
            });
        }
        private List<Integer> defaultContentSlots() {
            List<Integer> result = new ArrayList<>();
            for (int slot = 0; slot < rows * 9 - 9; slot++) result.add(slot);
            if (result.isEmpty()) throw new IllegalArgumentException("Paged GUI needs a content row and a navigation row");
            return result;
        }
        private void checkSlot(int slot) {
            if (slot < 0 || slot >= rows * 9) throw new IllegalArgumentException("Slot " + slot + " is outside a " + rows + " row chest");
        }
        private static GuiItemTemplate template(ItemStack item) {
            if (item == null) return null;
            ItemStack copy = item.clone();
            return context -> copy.clone();
        }
        private record Snapshot<T>(List<T> items, PageInfo page) { }
    }

    private static final class ContentComponent<T> implements GuiComponent {
        private final Builder<T> builder; private final GuiStateKey<Integer> pageKey; private final List<Integer> slots; private final int ordinal;
        private ContentComponent(Builder<T> builder, GuiStateKey<Integer> pageKey, List<Integer> slots, int ordinal) { this.builder=builder;this.pageKey=pageKey;this.slots=slots;this.ordinal=ordinal; }
        @Override public ItemStack render(GuiRenderContext context) {
            Builder.Snapshot<T> snapshot = builder.snapshot(context, pageKey, slots);
            int itemIndex = snapshot.page.firstVisibleItemIndex() + ordinal;
            if (itemIndex >= snapshot.items.size() || ordinal >= snapshot.page.visibleItemCount()) return null;
            GuiComponent component = builder.renderer.apply(context, snapshot.items.get(itemIndex));
            return component == null || !component.visible(context) ? null : component.render(context);
        }
        @Override public boolean interactive() { return true; }
        @Override public void click(GuiClickContext context) {
            GuiRenderContext renderContext = new GuiRenderContext(context.viewer(), context.menu(), context.session(), context.slot(), this);
            Builder.Snapshot<T> snapshot = builder.snapshot(renderContext, pageKey, slots);
            int itemIndex = snapshot.page.firstVisibleItemIndex() + ordinal;
            if (itemIndex >= snapshot.items.size() || ordinal >= snapshot.page.visibleItemCount()) return;
            GuiComponent component = builder.renderer.apply(renderContext, snapshot.items.get(itemIndex));
            if (component != null && component.visible(renderContext)) component.click(context);
        }
    }
    private static final class PageControl<T> implements GuiComponent {
        private final Builder<T> builder; private final GuiStateKey<Integer> pageKey; private final List<Integer> slots; private final boolean forward;
        private PageControl(Builder<T> b, GuiStateKey<Integer> k, List<Integer> slots, boolean forward) { builder=b;pageKey=k;this.slots=slots;this.forward=forward; }
        @Override public ItemStack render(GuiRenderContext context) {
            PageInfo page = builder.snapshot(context,pageKey,slots).page;
            boolean active = forward ? page.hasNext() : page.hasPrevious();
            GuiItemTemplate item = active ? forward ? builder.next : builder.previous : builder.disabled;
            if (item == null) item = renderContext -> active
                    ? GuiItems.of(Material.ARROW, forward ? "Next page" : "Previous page")
                    : GuiItems.of(Material.GRAY_STAINED_GLASS_PANE, forward ? "No next page" : "No previous page");
            return item.render(context);
        }
        @Override public boolean interactive() { return true; }
        @Override public void click(GuiClickContext context) {
            PageInfo page = builder.snapshot(new GuiRenderContext(context.viewer(),context.menu(),context.session(),context.slot(),this),pageKey,slots).page;
            if (forward ? page.hasNext() : page.hasPrevious()) context.session().set(pageKey,page.currentPage()+(forward?1:-1));
        }
    }
    private static final class Indicator<T> implements GuiComponent {
        private final Builder<T> b;private final GuiStateKey<Integer> key;private final List<Integer> slots;
        private Indicator(Builder<T>b,GuiStateKey<Integer>key,List<Integer>slots){this.b=b;this.key=key;this.slots=slots;}
        @Override public ItemStack render(GuiRenderContext context){PageInfo p=b.snapshot(context,key,slots).page;return b.indicator==null
                ?GuiItems.of(Material.PAPER,"Page "+p.currentPage()+"/"+p.totalPages())
                :Objects.requireNonNull(b.indicator.apply(context,p),"page indicator item").clone();}
    }
    private static final class EmptyComponent<T> implements GuiComponent {
        private final Builder<T>b;private final GuiStateKey<Integer>key;private final List<Integer>slots;
        private EmptyComponent(Builder<T>b,GuiStateKey<Integer>key,List<Integer>slots){this.b=b;this.key=key;this.slots=slots;}
        @Override public boolean visible(GuiRenderContext context){return b.snapshot(context,key,slots).items.isEmpty();}
        @Override public ItemStack render(GuiRenderContext context){return b.empty==null?GuiItems.of(Material.BARRIER,"No entries"):b.empty.render(context);}
    }
}
