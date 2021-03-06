package net.minecraft.src.buildcraft.factory.network;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

import net.minecraft.src.EntityPlayerMP;
import net.minecraft.src.NetServerHandler;
import net.minecraft.src.NetworkManager;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;
import net.minecraft.src.buildcraft.core.network.PacketIds;
import net.minecraft.src.buildcraft.core.network.PacketUpdate;
import net.minecraft.src.buildcraft.factory.TileRefinery;
import net.minecraft.src.forge.IPacketHandler;

public class PacketHandler implements IPacketHandler {

	@Override
	public void onPacketData(NetworkManager network, String channel, byte[] bytes) {
		
		DataInputStream data = new DataInputStream(new ByteArrayInputStream(bytes));
		try
		{
			NetServerHandler net = (NetServerHandler)network.getNetHandler();

			int packetID = data.read();
			PacketUpdate packet = new PacketUpdate();
			
			switch (packetID) {

			case PacketIds.REFINERY_FILTER_SET:
				packet.readData(data);
				onRefinerySelect(net.getPlayerEntity(), packet);
				break;

			}

		} catch(Exception ex) {
			ex.printStackTrace();
		}

	}

	private TileRefinery getRefinery(World world, int x, int y, int z) {
		if(!world.blockExists(x, y, z))
			return null;

		TileEntity tile = world.getBlockTileEntity(x, y, z);
		if(!(tile instanceof TileRefinery))
			return null;
		
		return (TileRefinery)tile;
	}
	
	private void onRefinerySelect(EntityPlayerMP playerEntity, PacketUpdate packet) {

		TileRefinery tile = getRefinery(playerEntity.worldObj, packet.posX, packet.posY, packet.posZ);
		if(tile == null)
			return;
		
		tile.setFilter(packet.payload.intPayload[0], packet.payload.intPayload[1]);
		
	}

}
