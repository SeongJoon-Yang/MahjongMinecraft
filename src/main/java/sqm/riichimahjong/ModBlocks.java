package sqm.riichimahjong;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public class ModBlocks {
    public static final Block MAHJONG_TABLE = registerBlock("mahjong_table",
            new sqm.riichimahjong.MahjongTableBlock(BlockBehaviour.Properties.of()
                    .strength(2.5f)
                    .mapColor(MapColor.WOOD)
                    .noOcclusion()));

    private static Block registerBlock(String name, Block block) {
        registerBlockItem(name, block);
        return Registry.register(BuiltInRegistries.BLOCK, RiichiMahjong.id(name), block);
    }
    
    private static void registerBlockItem(String name, Block block) {
        Registry.register(BuiltInRegistries.ITEM, RiichiMahjong.id(name),
                new BlockItem(block, new Item.Properties()));
    }

    public static void registerModBlocks() {
        RiichiMahjong.LOGGER.info("Registering mod blocks for " + RiichiMahjong.MOD_ID);

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> {
            entries.accept(MAHJONG_TABLE);
        });
    }
}