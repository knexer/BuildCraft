/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */

package net.minecraft.src.buildcraft.transport;


import java.util.Iterator;
import java.util.LinkedList;

import net.minecraft.src.Block;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.IInventory;
import net.minecraft.src.ItemStack;
import net.minecraft.src.Slot;
import net.minecraft.src.TileEntity;
import net.minecraft.src.buildcraft.api.APIProxy;
import net.minecraft.src.buildcraft.api.Action;
import net.minecraft.src.buildcraft.api.BuildCraftAPI;
import net.minecraft.src.buildcraft.api.Orientations;
import net.minecraft.src.buildcraft.api.Position;
import net.minecraft.src.buildcraft.api.Trigger;
import net.minecraft.src.buildcraft.api.TriggerParameter;
import net.minecraft.src.buildcraft.core.BuildCraftContainer;
import net.minecraft.src.buildcraft.core.CoreProxy;
import net.minecraft.src.buildcraft.core.network.PacketCoordinates;
import net.minecraft.src.buildcraft.core.network.PacketIds;
import net.minecraft.src.buildcraft.core.network.PacketPayload;
import net.minecraft.src.buildcraft.core.network.PacketUpdate;

public class CraftingGateInterface extends BuildCraftContainer {

	IInventory playerIInventory;
	Pipe pipe;
	
	private final LinkedList<Trigger> _potentialTriggers = new LinkedList <Trigger> ();
	private final LinkedList<Action> _potentialActions = new LinkedList <Action> ();

	private boolean isSynchronized = false;
	private boolean isNetInitialized = false;

	public CraftingGateInterface(IInventory playerInventory, Pipe pipe) {
		super(pipe.container.getSizeInventory());
		this.playerIInventory = playerInventory;

		for (int l = 0; l < 3; l++)
			for (int k1 = 0; k1 < 9; k1++)
				addSlot(new Slot(playerInventory, k1 + l * 9 + 9, 8 + k1 * 18,
						123 + l * 18));

		for (int i1 = 0; i1 < 9; i1++)
			addSlot(new Slot(playerInventory, i1, 8 + i1 * 18, 181));

		this.pipe = pipe;
		
		// Do not attempt to create a list of potential actions and triggers on the client.
		if(!APIProxy.isRemote()) {
			_potentialActions.addAll(pipe.getActions());
			_potentialTriggers.addAll (BuildCraftAPI.getPipeTriggers(pipe));

			for (Orientations o : Orientations.dirs()) {
				Position pos = new Position (pipe.xCoord, pipe.yCoord, pipe.zCoord, o);
				pos.moveForwards(1.0);
				TileEntity tile = pipe.worldObj.getBlockTileEntity((int) pos.x, (int) pos.y, (int) pos.z);
				int blockID = pipe.worldObj.getBlockId((int) pos.x, (int) pos.y, (int) pos.z);
				Block block = Block.blocksList [blockID];

				LinkedList <Trigger> nearbyTriggers = BuildCraftAPI.getNeighborTriggers(block, tile);

				for (Trigger t : nearbyTriggers)
					if (!_potentialTriggers.contains(t))
						_potentialTriggers.add(t);

				LinkedList <Action> nearbyActions = BuildCraftAPI.getNeighborActions(block, tile);

				for (Action a : nearbyActions)
					if (!_potentialActions.contains(a))
						_potentialActions.add(a);
			}
		}
	}

	@Override
	public boolean canInteractWith(EntityPlayer entityplayer) {
		return pipe.container.isUseableByPlayer(entityplayer);
	}
	
	/** CLIENT SIDE **/
	
	/**
	 * Marks client side gate container as needing to be synchronized with the server.
	 */
	public void markDirty() {
		isSynchronized = false;
	}
	
	/**
	 * Clears list of potential actions and refills it according to packet.
	 * @param packet
	 */
	public void updateActions(PacketUpdate packet) {
		
		_potentialActions.clear();
		int length = packet.payload.intPayload[0];
		
		for(int i = 0; i < length; i++)
			_potentialActions.add(BuildCraftAPI.actions[packet.payload.intPayload[i + 1]]);

	}
	
	/**
	 * Clears list of potential triggers and refills it according to packet.
	 * @param packet
	 */
	public void updateTriggers(PacketUpdate packet) {
		_potentialTriggers.clear();
		int length = packet.payload.intPayload[0];
		
		for(int i = 0; i < length; i++)
			_potentialTriggers.add(BuildCraftAPI.triggers[packet.payload.intPayload[i + 1]]);
	}
	
	/**
	 * Sets the currently selected actions and triggers according to packet.
	 * @param packet
	 */
	public void setSelection(PacketUpdate packet) {
		
	}
	
	public void sendSelectionChange(int position) {
		PacketPayload payload = new PacketPayload(6, 0, 0);
		
		payload.intPayload[0] = position;
		
		if(pipe.activatedTriggers[position] != null)
			payload.intPayload[1] = pipe.activatedTriggers[position].id;
		else
			payload.intPayload[1] = -1;
		if(pipe.activatedActions[position] != null)
			payload.intPayload[2] = pipe.activatedActions[position].id;
		else
			payload.intPayload[2] = -1;
		
		if(pipe.triggerParameters[position] != null && pipe.triggerParameters[position].stack != null) {
			payload.intPayload[3] = pipe.triggerParameters[position].stack.itemID;
			payload.intPayload[4] = pipe.triggerParameters[position].stack.stackSize;
			payload.intPayload[5] = pipe.triggerParameters[position].stack.getItemDamage();
		}
		
		CoreProxy.sendToServer(new PacketUpdate(PacketIds.GATE_SELECTION_CHANGE, pipe.xCoord, pipe.yCoord, pipe.zCoord, payload).getPacket());
	}
	
	/**
	 * Initializes the list of triggers and actions on the gate and (re-)requests the current selection on the gate if needed.
	 */
	public void synchronize() {
		
		if(!isNetInitialized && APIProxy.isRemote()) {
			isNetInitialized = true;
			CoreProxy.sendToServer(new PacketCoordinates(PacketIds.GATE_REQUEST_INIT, pipe.xCoord, pipe.yCoord, pipe.zCoord).getPacket());
		}
		
		if(!isSynchronized && APIProxy.isRemote()) {
			isSynchronized = true;
			CoreProxy.sendToServer(new PacketCoordinates(PacketIds.GATE_REQUEST_SELECTION, pipe.xCoord, pipe.yCoord, pipe.zCoord).getPacket());
		}
		
	}
	
	/** SERVER SIDE **/
	
	public void handleInitRequest(EntityPlayer player) {
		sendActions(player);
		sendTriggers(player);
		sendSelection(player);
	}
	
	public void handleSelectionChange(PacketUpdate packet) {
		PacketPayload payload = packet.payload;
		int position = payload.intPayload[0];
		
		if(payload.intPayload[1] >= 0 && payload.intPayload[1] < BuildCraftAPI.triggers.length)
			setTrigger(position, BuildCraftAPI.triggers[payload.intPayload[1]]);
		else
			setTrigger(position, null);
		if(payload.intPayload[2] >= 0 && payload.intPayload[2] < BuildCraftAPI.actions.length)
			setAction(position, BuildCraftAPI.actions[payload.intPayload[2]]);
		else
			setAction(position, null);
		
		int itemID = payload.intPayload[3];
		if(itemID <= 0) {
			setTriggerParameter(position, null);
			return;
		}
			
		TriggerParameter param = new TriggerParameter();
		param.set(new ItemStack(itemID, payload.intPayload[4], payload.intPayload[5]));
		setTriggerParameter(position, param);
	}
	
	/**
	 * Sends the list of potential actions to the client
	 * @param player
	 */
	private void sendActions(EntityPlayer player) {
		
		// Compose update packet
		int length = _potentialActions.size();
		PacketPayload payload = new PacketPayload(length + 1, 0, 0);
		
		payload.intPayload[0] = length;
		for(int i = 0; i < length; i++)
			payload.intPayload[i + 1] = _potentialActions.get(i).id;
		
		PacketUpdate packet = new PacketUpdate(PacketIds.GATE_ACTIONS, pipe.xCoord, pipe.yCoord, pipe.zCoord, payload);
		
		// Send to player
		CoreProxy.sendToPlayer(player, packet);
	}
	
	/**
	 * Sends the list of potential triggers to the client
	 * @param player
	 */
	private void sendTriggers(EntityPlayer player) {
		
		// Compose update packet
		int length = _potentialTriggers.size();
		PacketPayload payload = new PacketPayload(length + 1, 0, 0);
		
		payload.intPayload[0] = length;
		for(int i = 0; i < length; i++)
			payload.intPayload[i + 1] = _potentialTriggers.get(i).id;
		
		PacketUpdate packet = new PacketUpdate(PacketIds.GATE_TRIGGERS, pipe.xCoord, pipe.yCoord, pipe.zCoord, payload);
		
		// Send to player
		CoreProxy.sendToPlayer(player, packet);
	}
	
	/**
	 * Sends the current selection on the gate to the client.
	 * @param player
	 */
	public void sendSelection(EntityPlayer player) {
		
	}
	
	/** TRIGGERS **/
	public boolean hasTriggers(){
		return _potentialTriggers.size() > 0;
	}
	
	public Trigger getFirstTrigger(){
		return _potentialTriggers.size() > 0 ? _potentialTriggers.getFirst() : null; 
	}
	
	public Trigger getLastTrigger(){
		return _potentialTriggers.size() > 0 ? _potentialTriggers.getLast() : null;
	}
	
	public Iterator<Trigger> getTriggerIterator(boolean descending){
		return descending ? _potentialTriggers.descendingIterator():_potentialTriggers.iterator(); 
	}
	
	public boolean isNearbyTriggerActive(Trigger trigger, TriggerParameter parameter){
		return pipe.isNearbyTriggerActive(trigger, parameter);
	}
	
	public void setTrigger(int position, Trigger trigger){
		if(APIProxy.isRemote())
			sendSelectionChange(position);
		pipe.setTrigger(position, trigger);
	}
	
	public void setTriggerParameter(int position, TriggerParameter parameter){
		if(APIProxy.isRemote())
			sendSelectionChange(position);
		pipe.setTriggerParameter(position, parameter);
	}

	
	/** ACTIONS **/
	public boolean hasActions() {
		return _potentialActions.size() > 0;
	}
	
	public Action getFirstAction(){
		return _potentialActions.size() > 0 ? _potentialActions.getFirst() : null;
	}
	
	public Action getLastAction() {
		return _potentialActions.size() > 0 ? _potentialActions.getLast() : null;
	}
	
	public Iterator<Action> getActionIterator(boolean descending) {
		return descending ? _potentialActions.descendingIterator() : _potentialActions.iterator();
	}
	
	public void setAction(int position, Action action){
		if(APIProxy.isRemote())
			sendSelectionChange(position);
		pipe.setAction(position, action);
	}
	
	/** GATE INFORMATION **/
	public String getGateGuiFile(){
		return pipe.gate.getGuiFile();
	}
	
	public int getGateOrdinal(){
		return pipe.gate.kind.ordinal();
	}
	
	public String getGateName(){
		return pipe.gate.getName();
	}

}