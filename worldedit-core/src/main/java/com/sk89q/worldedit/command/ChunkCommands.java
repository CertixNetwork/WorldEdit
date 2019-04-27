/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.command;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.sk89q.worldedit.command.util.Logging.LogMode.REGION;

import com.sk89q.worldedit.LocalConfiguration;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.command.util.CommandPermissions;
import com.sk89q.worldedit.command.util.CommandPermissionsConditionGenerator;
import com.sk89q.worldedit.command.util.Logging;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.MathUtils;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.util.formatting.component.PaginationBox;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.world.storage.LegacyChunkStore;
import com.sk89q.worldedit.world.storage.McRegionChunkStore;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Commands for working with chunks.
 */
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class ChunkCommands {

    private final WorldEdit worldEdit;

    public ChunkCommands(WorldEdit worldEdit) {
        checkNotNull(worldEdit);
        this.worldEdit = worldEdit;
    }

    @Command(
        name = "chunkinfo",
        desc = "Get information about the chunk that you are inside"
    )
    @CommandPermissions("worldedit.chunkinfo")
    public void chunkInfo(Player player) throws WorldEditException {
        Location pos = player.getBlockIn();
        int chunkX = (int) Math.floor(pos.getBlockX() / 16.0);
        int chunkZ = (int) Math.floor(pos.getBlockZ() / 16.0);

        String folder1 = Integer.toString(MathUtils.divisorMod(chunkX, 64), 36);
        String folder2 = Integer.toString(MathUtils.divisorMod(chunkZ, 64), 36);
        String filename = "c." + Integer.toString(chunkX, 36)
                + "." + Integer.toString(chunkZ, 36) + ".dat";

        player.print("Chunk: " + chunkX + ", " + chunkZ);
        player.print("Old format: " + folder1 + "/" + folder2 + "/" + filename);
        player.print("McRegion: region/" + McRegionChunkStore.getFilename(
                BlockVector2.at(chunkX, chunkZ)));
    }

    @Command(
        name = "listchunks",
        desc = "List chunks that your selection includes"
    )
    @CommandPermissions("worldedit.listchunks")
    public void listChunks(Player player, LocalSession session,
            @Arg(desc = "Page number.", def = "1") int page) throws WorldEditException {
        Set<BlockVector2> chunks = session.getSelection(player.getWorld()).getChunks();

        PaginationBox paginationBox = new PaginationBox("Selected Chunks", "/listchunks %page%");
        paginationBox.setComponents(chunks.stream().map(chunk -> TextComponent.of(chunk.toString())).collect(Collectors.toList()));
        player.print(paginationBox.create(page));
    }

    @Command(
        name = "delchunks",
        desc = "Delete chunks that your selection includes"
    )
    @CommandPermissions("worldedit.delchunks")
    @Logging(REGION)
    public void deleteChunks(Player player, LocalSession session) throws WorldEditException {
        player.print("Note that this command does not yet support the mcregion format.");
        LocalConfiguration config = worldEdit.getConfiguration();

        Set<BlockVector2> chunks = session.getSelection(player.getWorld()).getChunks();
        FileOutputStream out = null;

        if (config.shellSaveType == null) {
            player.printError("Shell script type must be configured: 'bat' or 'bash' expected.");
        } else if (config.shellSaveType.equalsIgnoreCase("bat")) {
            try {
                out = new FileOutputStream("worldedit-delchunks.bat");
                OutputStreamWriter writer = new OutputStreamWriter(out, "UTF-8");
                writer.write("@ECHO off\r\n");
                writer.write("ECHO This batch file was generated by WorldEdit.\r\n");
                writer.write("ECHO It contains a list of chunks that were in the selected region\r\n");
                writer.write("ECHO at the time that the /delchunks command was used. Run this file\r\n");
                writer.write("ECHO in order to delete the chunk files listed in this file.\r\n");
                writer.write("ECHO.\r\n");
                writer.write("PAUSE\r\n");

                for (BlockVector2 chunk : chunks) {
                    String filename = LegacyChunkStore.getFilename(chunk);
                    writer.write("ECHO " + filename + "\r\n");
                    writer.write("DEL \"world/" + filename + "\"\r\n");
                }

                writer.write("ECHO Complete.\r\n");
                writer.write("PAUSE\r\n");
                writer.close();
                player.print("worldedit-delchunks.bat written. Run it when no one is near the region.");
            } catch (IOException e) {
                player.printError("Error occurred: " + e.getMessage());
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ignored) { }
                }
            }
        } else if (config.shellSaveType.equalsIgnoreCase("bash")) {
            try {
                out = new FileOutputStream("worldedit-delchunks.sh");
                OutputStreamWriter writer = new OutputStreamWriter(out, "UTF-8");
                writer.write("#!/bin/bash\n");
                writer.write("echo This shell file was generated by WorldEdit.\n");
                writer.write("echo It contains a list of chunks that were in the selected region\n");
                writer.write("echo at the time that the /delchunks command was used. Run this file\n");
                writer.write("echo in order to delete the chunk files listed in this file.\n");
                writer.write("echo\n");
                writer.write("read -p \"Press any key to continue...\"\n");

                for (BlockVector2 chunk : chunks) {
                    String filename = LegacyChunkStore.getFilename(chunk);
                    writer.write("echo " + filename + "\n");
                    writer.write("rm \"world/" + filename + "\"\n");
                }

                writer.write("echo Complete.\n");
                writer.write("read -p \"Press any key to continue...\"\n");
                writer.close();
                player.print("worldedit-delchunks.sh written. Run it when no one is near the region.");
                player.print("You will have to chmod it to be executable.");
            } catch (IOException e) {
                player.printError("Error occurred: " + e.getMessage());
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        } else {
            player.printError("Shell script type must be configured: 'bat' or 'bash' expected.");
        }
    }

}
