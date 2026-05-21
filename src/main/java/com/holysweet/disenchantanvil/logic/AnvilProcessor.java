package com.holysweet.disenchantanvil.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class AnvilProcessor {

    private AnvilProcessor() {}

    public static void processLanding(Level level, BlockPos anvilPos) {
        // All item entities inside the anvil block space
        AABB box = new AABB(
                anvilPos.getX(), anvilPos.getY(), anvilPos.getZ(),
                anvilPos.getX() + 1, anvilPos.getY() + 1, anvilPos.getZ() + 1
        );
        List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, box, ItemEntity::isAlive);
        if (nearby.isEmpty()) return;

        // Classify
        List<ItemEntity> plainBooks = new ArrayList<>();
        List<ItemEntity> enchantedBooks = new ArrayList<>();
        List<ItemEntity> gear = new ArrayList<>();

        for (ItemEntity it : nearby) {
            ItemStack st = it.getItem();
            if (st.is(Items.BOOK)) {
                plainBooks.add(it);
            } else if (st.is(Items.ENCHANTED_BOOK)) {
                ItemEnchantments stored = st.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
                if (!stored.isEmpty()) enchantedBooks.add(it);
            } else {
                ItemEnchantments ench = st.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
                if (!ench.isEmpty()) gear.add(it);
            }
        }

        // Safety: item + enchanted book but NO plain books => do nothing (prevents accidental wipes)
        if (!gear.isEmpty() && !enchantedBooks.isEmpty() && plainBooks.isEmpty()) {
            return;
        }

        boolean changed = false;

        // ---- ITEM -> BOOK extraction: process ALL gear, consume up to #plainBooks total ----
        int booksAvailable = countPlainBooks(plainBooks);
        if (!gear.isEmpty() && booksAvailable > 0) {
            for (ItemEntity gearEnt : gear) {
                if (booksAvailable <= 0) break;

                ItemStack gearStack = gearEnt.getItem();
                ItemEnchantments ench = gearStack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
                if (ench.isEmpty()) continue;

                // ordered per-item list: curses first, then others
                List<Holder<Enchantment>> toRemoveOrdered = listEnchantsCursesFirst(ench);
                if (toRemoveOrdered.isEmpty()) continue;

                ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(ench);
                int removedFromThisItem = 0;

                for (Holder<Enchantment> target : toRemoveOrdered) {
                    if (booksAvailable <= 0) break;

                    int lvl = ench.getLevel(target);
                    if (lvl <= 0) continue;

                    // consume a plain book; if none left mid-loop, bail
                    if (!consumeOnePlainBook(plainBooks)) {
                        booksAvailable = 0;
                        break;
                    }
                    booksAvailable--;

                    // remove from item + spawn enchanted book with that single enchant
                    m.set(target, 0); // 0 = remove
                    ItemStack outBook = makeSingleEnchantBook(target, lvl);
                    spawnItem(level, anvilPos, outBook);

                    removedFromThisItem++;
                }

                if (removedFromThisItem > 0) {
                    gearStack.set(DataComponents.ENCHANTMENTS, m.toImmutable());
                    resetAnvilPenalty(gearStack);
                    changed = true;
                }
            }
        }

        // ---- ENCHANTED BOOK SPLIT: for each book with >1 ench, create one book per enchant, remove original ----
        if (!enchantedBooks.isEmpty()) {
            for (ItemEntity bookEnt : enchantedBooks) {
                ItemStack book = bookEnt.getItem();
                ItemEnchantments stored = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
                int count = stored.size();
                if (count > 1) {
                    // explode into single-enchant books (curses-first order for aesthetics)
                    for (Holder<Enchantment> h : listEnchantsCursesFirst(stored)) {
                        int lvl = stored.getLevel(h);
                        if (lvl <= 0) continue;
                        ItemStack out = makeSingleEnchantBook(h, lvl);
                        spawnItem(level, anvilPos, out);
                    }
                    // remove the original multi-enchant book (avoid leaving empty enchanted book)
                    book.shrink(1);
                    if (book.isEmpty()) bookEnt.discard();
                    changed = true;
                } else {
                    // 0 enchants shouldn't happen (we filtered), 1 enchant: leave it as-is
                }
            }
        }

        if (changed) {
            ejectNearbyItems(level, anvilPos, 0.9);
        }
    }

    // ----- Helpers -----

    /** Count total number of BOOK items across nearby ItemEntities. */
    private static int countPlainBooks(List<ItemEntity> plainBooks) {
        int total = 0;
        for (ItemEntity e : plainBooks) total += e.getItem().getCount();
        return total;
    }

    /** Consume 1 plain book from the list; discards empty item entities. Returns true if consumed. */
    private static boolean consumeOnePlainBook(List<ItemEntity> plainBooks) {
        for (Iterator<ItemEntity> it = plainBooks.iterator(); it.hasNext(); ) {
            ItemEntity ent = it.next();
            ItemStack st = ent.getItem();
            if (!st.is(Items.BOOK) || st.isEmpty()) {
                it.remove();
                continue;
            }
            st.shrink(1);
            if (st.isEmpty()) {
                ent.discard();
                it.remove();
            }
            return true;
        }
        return false;
    }

    /** Return enchant list with curses first (stable order within each group). */
    private static List<Holder<Enchantment>> listEnchantsCursesFirst(ItemEnchantments ench) {
        List<Holder<Enchantment>> curses = new ArrayList<>();
        List<Holder<Enchantment>> normals = new ArrayList<>();
        for (var e : ench.entrySet()) {
            Holder<Enchantment> h = e.getKey();
            if (h.is(EnchantmentTags.CURSE)) curses.add(h);
            else normals.add(h);
        }
        curses.addAll(normals);
        return curses;
    }

    /** Pick "first" enchant: curse first else first normal. */
    private static Holder<Enchantment> pickFirst(ItemEnchantments ench) {
        if (ench.isEmpty()) return null;
        Holder<Enchantment> firstNormal = null;
        for (var e : ench.entrySet()) {
            Holder<Enchantment> h = e.getKey();
            if (h.is(EnchantmentTags.CURSE)) return h;
            if (firstNormal == null) firstNormal = h;
        }
        return firstNormal;
    }

    /** Create an enchanted book with exactly one stored enchantment. */
    private static ItemStack makeSingleEnchantBook(Holder<Enchantment> ench, int level) {
        ItemStack out = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        m.set(ench, level);
        out.set(DataComponents.STORED_ENCHANTMENTS, m.toImmutable());
        resetAnvilPenalty(out);
        return out;
    }

    /** Spawn an item stack centered slightly above the anvil. */
    private static void spawnItem(Level level, BlockPos pos, ItemStack stack) {
        ItemEntity outEnt = new ItemEntity(level,
                pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, stack);
        outEnt.setDefaultPickUpDelay();
        level.addFreshEntity(outEnt);
    }

    /** Reset the anvil "prior work" penalty so XP costs don't snowball. */
    private static void resetAnvilPenalty(ItemStack stack) {
        stack.remove(DataComponents.REPAIR_COST);
    }

    /** Push nearby item entities away from the anvil so drops don't jam. */
    public static void ejectNearbyItems(Level level, BlockPos anvilPos, double radius) {
        AABB box = new AABB(
                anvilPos.getX() + 0.5 - radius, anvilPos.getY() - 0.25, anvilPos.getZ() + 0.5 - radius,
                anvilPos.getX() + 0.5 + radius, anvilPos.getY() + 1.25, anvilPos.getZ() + 0.5 + radius
        );
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box, ItemEntity::isAlive);
        if (!items.isEmpty()) {
            pushOutItems(items, anvilPos);
        }
    }

    private static void pushOutItems(List<ItemEntity> items, BlockPos anvilPos) {
        final double nudge = 0.08;
        final double lift  = 0.02;
        int i = 0;
        for (ItemEntity it : items) {
            if (!it.isAlive()) continue;
            int dir = (i++) & 3;
            double dx = (dir == 0) ? nudge : (dir == 1) ? -nudge : 0.0;
            double dz = (dir == 2) ? nudge : (dir == 3) ? -nudge : 0.0;
            double px = anvilPos.getX() + 0.5 + dx * 2.0;
            double py = anvilPos.getY() + 0.2;
            double pz = anvilPos.getZ() + 0.5 + dz * 2.0;
            it.setPos(px, py, pz);
            it.setDeltaMovement(dx, lift, dz);
            it.setOnGround(false);
            it.hurtMarked = true;
        }
    }
}
