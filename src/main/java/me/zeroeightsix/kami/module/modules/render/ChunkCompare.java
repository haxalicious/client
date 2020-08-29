package me.zeroeightsix.kami.module.modules.render;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.event.events.RenderEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import me.zeroeightsix.kami.util.*;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.management.PlayerProfileCache;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.ChunkGeneratorEnd;
import net.minecraft.world.gen.ChunkGeneratorHell;
import net.minecraft.world.gen.ChunkGeneratorOverworld;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.event.world.ChunkEvent;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static me.zeroeightsix.kami.util.ColourUtils.toRGBA;
import static me.zeroeightsix.kami.util.MessageSendHelper.*;

/**
 * @author Haxalicious
 * Search module by wnuke
 * Updated by dominikaaaa on 20/04/20
 * Updated by Afel on 08/06/20
 */
@Module.Info(
        name = "ChunkCompare",
        description = "Terrain exploit but in reverse",
        category = Module.Category.RENDER
)
public class ChunkCompare extends Module {
    private final Setting<String> seedString = register(Settings.stringBuilder("Seed").withValue("0").build());
    private final Setting<Integer> alpha = register(Settings.integerBuilder("Transparency").withMinimum(1).withMaximum(255).withValue(120).build());
    private final Setting<Integer> chunkDiscardThreshold = register(Settings.integerBuilder("ChunkDiscardThreshold").withMinimum(0).withValue(1000).build());
    private final Setting<Boolean> tracers = register(Settings.booleanBuilder("Tracers").withValue(true).build());
    private final Setting<Boolean> debug = register(Settings.booleanBuilder("Debug").withValue(false).build());

    public long seed = Long.parseLong(seedString.getValue());
    private IntegratedServer mcServer;

    ScheduledExecutorService exec = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread t = new Thread(r);
                t.setPriority(Thread.NORM_PRIORITY - 2); //LOW priority
                return t;
            });

    @Override
    public void onEnable() {
        String s = seedString.getValue();
        if (chunkDiscardThreshold.getValue() == 0) {
            sendWarningMessage(getChatName() + " ChunkDiscardThreshold is set to 0! This may result in massive lag spikes and/or game freezes!");
        }
        if(s.equals("0")) {
            sendErrorMessage(getChatName() + " Seed is not set!");
            sendWarningMessage(getChatName() + " Set the seed with &7" + Command.getCommandPrefix() + "set ChunkCompare Seed&f seed");
            disable();
            return;
        }
        seed = Long.parseLong(s);
        YggdrasilAuthenticationService dummyAuth = new YggdrasilAuthenticationService(mc.proxy, UUID.randomUUID().toString());
        MinecraftSessionService dummySession = dummyAuth.createMinecraftSessionService();
        GameProfileRepository dummyProfile = dummyAuth.createProfileRepository();
        PlayerProfileCache gameFolder = new PlayerProfileCache(dummyProfile, new File(mc.gameDir, MinecraftServer.USER_CACHE_FILE.getName()));
        WorldSettings worldSettings = new WorldSettings(seed, GameType.SURVIVAL, true, false, WorldType.DEFAULT);
        mcServer = new IntegratedServer(mc, s, s, worldSettings, dummyAuth, dummySession, dummyProfile, gameFolder);
        mcServer.loadAllWorlds(s, s, seed, WorldType.DEFAULT, "");
        startTime = 0;
    }

    @Override
    protected void onDisable() {
        mainList.clear();
    }

    private long startTime = 0;
    public static final Map<ChunkPos, Map<BlockPos, Tuple<Integer, Integer>>> mainList = new ConcurrentHashMap<>();


    @EventHandler
    public Listener<ChunkEvent.Load> chunkLoadListener = new Listener<>(event -> {
        if (isEnabled()) {
            if(mc.world != null && mc.player != null) {
                /*exec.execute(() -> {
                    IChunkGenerator chunkGenerator;
                    if(mc.player.dimension == 0) {
                        chunkGenerator = new ChunkGeneratorOverworld(mc.world, seed, true, "");
                    }
                    else if(mc.player.dimension == -1) {

                    }
                    else {

                    }
                    Chunk referenceChunk = event.getChunk();
                    ChunkPos tempPos = referenceChunk.getPos();
                });
                */
                exec.schedule(() -> {
                    Chunk serverChunk = event.getChunk();
                    ChunkPos pos = serverChunk.getPos();
                    int x = pos.x;
                    int z = pos.z;
                    Chunk generatedChunk = mcServer.getWorld(mc.player.dimension).getChunkProvider().chunkGenerator.generateChunk(x, z);
                    Chunk trash1 = mcServer.getWorld(mc.player.dimension).getChunkProvider().chunkGenerator.generateChunk(x - 1, z);
                    Chunk trash2 = mcServer.getWorld(mc.player.dimension).getChunkProvider().chunkGenerator.generateChunk(x, z -1);
                    Chunk trash3 = mcServer.getWorld(mc.player.dimension).getChunkProvider().chunkGenerator.generateChunk(x - 1, z - 1);
                    /*ExtendedBlockStorage[] chunkStorage = generatedChunk.getBlockStorageArray();
                    ExtendedBlockStorage[] chunkSnapshot = new ExtendedBlockStorage[16];
                    ExtendedBlockStorage[] chunkSnapshotFinal = new ExtendedBlockStorage[16];
                    System.arraycopy(chunkStorage, 0, chunkSnapshot, 0, chunkSnapshot.length);
                    serverChunk = mc.world.getChunkProvider().provideChunk(x, z); // Super lazy way to get rid of ghost chunks
                    int min = 69420;
                    for(int i = -1; i <= 0; i++) { // Unoptimized mess of cancer, idk how to fix it tho. At least arraycopy is fast.
                        for(int j = -1; j <= 0; j++) {
                            if(populateChunks(serverChunk, generatedChunk, x + i, z + j) < min) {
                                ExtendedBlockStorage[] temp = generatedChunk.getBlockStorageArray();
                                System.arraycopy(temp, 0, chunkSnapshotFinal, 0, chunkSnapshotFinal.length);
                            }
                            generatedChunk.setStorageArrays(chunkSnapshot);
                        }
                    }
                    generatedChunk.setStorageArrays(chunkSnapshotFinal);*/
                    for(int i = -1; i <= 0; i++) { // Unoptimized mess of cancer, idk how to fix it tho. At least arraycopy is fast.
                        for(int j = -1; j <= 0; j++) {
                            mcServer.getWorld(mc.player.dimension).getChunkProvider().chunkGenerator.populate(x, z);
                        }
                    }
                    compareChunks(serverChunk, generatedChunk);
                }, 100, MILLISECONDS);
            }
        }
    });

    @EventHandler
    public Listener<ChunkEvent.Unload> chunkUnloadListener = new Listener<>(event -> {
        if (isEnabled())
            mainList.remove(event.getChunk().getPos());
    });

    Map<BlockPos, Tuple<Integer, Integer>> blocksToShow;

    @Override
    public void onWorldRender(RenderEvent event) {
        if (mainList != null && shouldUpdate()) {
            blocksToShow = mainList.values().stream()
                    .flatMap((e) -> e.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        if (blocksToShow != null) {
            GlStateManager.pushMatrix();
            KamiTessellator.prepare(GL11.GL_QUADS);
            for (Map.Entry<BlockPos, Tuple<Integer, Integer>> entry : blocksToShow.entrySet()) {
                KamiTessellator.drawBox(entry.getKey(), entry.getValue().getFirst(), entry.getValue().getSecond());
            }
            KamiTessellator.release();
            GlStateManager.popMatrix();
            GlStateManager.enableTexture2D();

            if (tracers.getValue()) {
                for (Map.Entry<BlockPos, Tuple<Integer, Integer>> entry : blocksToShow.entrySet()) {
                    KamiTessellator.drawLineToBlock(entry.getKey(), entry.getValue().getFirst(), ((float) alpha.getValue()) / 255);
                }
            }
        }
    }

    public int populateChunks(Chunk chunk1, Chunk chunk2, int x, int z) {
        ChunkPos pos = chunk1.getPos();
        BlockPos pos1 = new BlockPos(pos.getXStart(), 10, pos.getZStart());
        BlockPos pos2 = new BlockPos(pos.getXEnd(), 60, pos.getZEnd());
        Iterable<BlockPos> blocks = BlockPos.getAllInBox(pos1, pos2);
        int i = 0;
        int dim = mc.player.dimension;
        mcServer.getWorld(dim).getChunkProvider().chunkGenerator.populate(x, z);
        try {
            for (BlockPos blockPos : blocks) {
                int id1 = Block.getIdFromBlock(chunk1.getBlockState(blockPos).getBlock());
                int id2 = Block.getIdFromBlock(chunk2.getBlockState(blockPos).getBlock());
                if (id1 != id2) {
                    i++;
                }
            }
        } catch (NullPointerException ignored) {
        }
        return i;
    }

    public void compareChunks(Chunk chunk1, Chunk chunk2) {
        ChunkPos pos = chunk1.getPos();
        BlockPos pos1 = new BlockPos(pos.getXStart(), 0, pos.getZStart());
        BlockPos pos2 = new BlockPos(pos.getXEnd(), 255, pos.getZEnd());
        Iterable<BlockPos> blocks = BlockPos.getAllInBox(pos1, pos2);
        Map<BlockPos, Tuple<Integer, Integer>> found = new HashMap<>();
        int dim = mc.player.dimension;
        mcServer.getWorld(dim).getChunkProvider().chunkGenerator.populate(pos.x, pos.z);
        try {
            for (BlockPos blockPos : blocks) {
                int side = GeometryMasks.Quad.ALL;
                Block block = chunk1.getBlockState(blockPos).getBlock();
                int id1 = Block.getIdFromBlock(block);
                int id2 = Block.getIdFromBlock(chunk2.getBlockState(blockPos).getBlock()); // Get server-side block
                switch(dim) {
                    case -1:
                        id1 = mapBlockNether(id1);
                        //id2 = mapBlockNether(id2);
                        break;
                    case 0:
                        id1 = mapBlockOverworld(id1);
                        id2 = mapBlockOverworld(id2);
                        break;
                    case 1:
                        id1 = mapBlockEnd(id1);
                        //id2 = mapBlockEnd(id2);
                        break;
                }

                if (id1 != id2) {
                    if(debug.getValue() == true) {
                        sendChatMessage("Block mismatch at " + blockPos.toString() + ", Provided: " + String.valueOf(id1) + ", Expected: " + String.valueOf(id2));
                    }
                    Tuple<Integer, Integer> tuple = getTuple(side, block);
                    found.put(blockPos, tuple);
                }
            }
        } catch (NullPointerException ignored) {
        } // Fix ghost chunks getting loaded and generating NullPointerExceptions
        if (chunkDiscardThreshold.getValue() != 0 && found.size() > chunkDiscardThreshold.getValue()) {
            found.clear();
            if(debug.getValue() == true) {
                sendChatMessage("Ignoring chunk at " + String.valueOf(pos.x) + ", " + String.valueOf(pos.z));
            }
        }
        if (!found.isEmpty()) {
            Map<BlockPos, Tuple<Integer, Integer>> actual = ChunkCompare.mainList.computeIfAbsent(pos, (p) -> new ConcurrentHashMap<>());
            actual.clear();
            actual.putAll(found);
        }
    }

    public int mapBlockOverworld(int id) {
        switch(id) {
            case 8:
            case 17:
            case 18:
            case 30:
            case 31:
            case 32:
            case 37:
            case 38:
            case 39:
            case 40:
            case 81:
            case 83:
            case 86:
            case 106:
            case 111:
            case 127:
            case 161:
            case 162:
            case 175:
                id = 0;
                break;
            case 7:
            case 13:
            case 14:
            case 15:
            case 16:
            case 21:
            case 56:
            case 73:
            case 97:
            case 129:
                id = 1;
                break;
            default:
                break;
        }
        return id;
    }

    public int mapBlockNether(int id) {
        switch(id) {
            case 10:
            case 39:
            case 40:
            case 51:
            case 89:
                id = 0;
                break;
            case 88:
            case 153:
            case 213:
                id = 87;
                break;
        }
        return id;
    }

    public int mapBlockEnd(int id) {
        switch(id) {
            case 102:
            case 198:
            case 199:
            case 200:
            case 201:
            case 202:
            case 203:
            case 204:
            case 205:
            case 206:
                id = 0;
                break;
        }
        return id;
    }

    private Tuple<Integer, Integer> getTuple(int side, Block block) {
        int blockColor = toRGBA(255, 0, 0, alpha.getValue());
        return new Tuple<>(blockColor, side);
    }

    private long previousTime = 0;

    private boolean shouldUpdate() {
        if (previousTime + 100 <= System.currentTimeMillis()) {
            previousTime = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    private long compareTime = 0;

    public static class Triplet<T, U, V> {

        private final T first;
        private final U second;
        private final V third;

        public Triplet(T first, U second, V third) {
            this.first = first;
            this.second = second;
            this.third = third;
        }

        public T getFirst() {
            return first;
        }

        public U getSecond() {
            return second;
        }

        public V getThird() {
            return third;
        }
    }
}