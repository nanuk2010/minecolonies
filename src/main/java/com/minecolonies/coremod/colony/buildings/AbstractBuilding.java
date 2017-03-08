package com.minecolonies.coremod.colony.buildings;

import com.minecolonies.blockout.views.Window;
import com.minecolonies.coremod.blocks.*;
import com.minecolonies.coremod.colony.CitizenData;
import com.minecolonies.coremod.colony.Colony;
import com.minecolonies.coremod.colony.ColonyManager;
import com.minecolonies.coremod.colony.ColonyView;
import com.minecolonies.coremod.colony.buildings.views.BuildingBuilderView;
import com.minecolonies.coremod.colony.requestsystem.request.IRequest;
import com.minecolonies.coremod.colony.requestsystem.token.IToken;
import com.minecolonies.coremod.colony.requestsystem.requestable.Tool;
import com.minecolonies.coremod.colony.workorders.WorkOrderBuild;
import com.minecolonies.coremod.entity.ai.citizen.builder.ConstructionTapeHelper;
import com.minecolonies.coremod.entity.ai.item.handling.ItemStorage;
import com.minecolonies.coremod.tileentities.TileEntityColonyBuilding;
import com.minecolonies.coremod.util.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * Base building class, has all the foundation for what a building stores and does.
 */
public abstract class AbstractBuilding implements IBuilding {
    /**
     * Tag used to store the containers to NBT.
     */
    private static final String TAG_CONTAINERS = "Containers";

    /**
     * The tag to store the building type.
     */
    private static final String TAG_BUILDING_TYPE = "type";

    /**
     * The tag to store the building location.
     * Location is unique (within a Colony) and so can double as the Id.
     */
    private static final String TAG_LOCATION = "location";

    /**
     * The tag to store the level of the building.
     */
    private static final String TAG_BUILDING_LEVEL = "level";

    /**
     * The tag to store the rotation of the building.
     */
    private static final String TAG_ROTATION = "rotation";

    /**
     * The tag to store the style of the building.
     */
    private static final String                  TAG_STYLE                    = "style";
    private static final int                     NO_WORK_ORDER                = 0;

    /**
     * List of all open requests made by this building.
     *
     * The key in this map is the class for the request type.
     * The value is a list of tokens that represent the open requests inside the colony.
     */
    @NotNull
    private final Map<Class, List<IToken>> openRequests = new HashMap<>();

    /**
     * Map used to identify which citizen assigned to this building made which request.
     */
    @NotNull
    private final Map<IToken, Integer> requestsByCitizen = new HashMap<>();

    /**
     * Map used to identify which requests a given citizen has open.
     */
    @NotNull
    private final Map<Integer, List<IToken>> citizensByRequests = new HashMap<>();

    /**
     * Map to resolve names to class.
     */
    @NotNull
    private static final Map<String, Class<?>>   nameToClassMap               = new HashMap<>();
    /**
     * Map to resolve classes to name.
     */
    @NotNull
    private static final Map<Class<?>, String>   classToNameMap               = new HashMap<>();
    /**
     * Map to resolve block to building class.
     */
    @NotNull
    private static final Map<Class<?>, Class<?>> blockClassToBuildingClassMap = new HashMap<>();
    /**
     * Map to resolve classNameHash to class.
     */
    @NotNull
    private static final Map<Integer, Class<?>>  classNameHashToViewClassMap  = new HashMap<>();
    /*
     * Add all the mappings.
     */
    static
    {
        addMapping("Baker", BuildingBaker.class, BuildingBaker.View.class, BlockHutBaker.class);
        addMapping("Blacksmith", BuildingBlacksmith.class, BuildingBlacksmith.View.class, BlockHutBlacksmith.class);
        addMapping("Builder", BuildingBuilder.class, BuildingBuilderView.class, BlockHutBuilder.class);
        addMapping("Home", BuildingHome.class, BuildingHome.View.class, BlockHutCitizen.class);
        addMapping("Farmer", BuildingFarmer.class, BuildingFarmer.View.class, BlockHutFarmer.class);
        addMapping("Lumberjack", BuildingLumberjack.class, BuildingLumberjack.View.class, BlockHutLumberjack.class);
        addMapping("Miner", BuildingMiner.class, BuildingMiner.View.class, BlockHutMiner.class);
        addMapping("Stonemason", BuildingStonemason.class, BuildingStonemason.View.class, BlockHutStonemason.class);
        addMapping("TownHall", BuildingTownHall.class, BuildingTownHall.View.class, BlockHutTownHall.class);
        addMapping("Deliveryman", BuildingDeliveryman.class, BuildingDeliveryman.View.class, BlockHutDeliveryman.class);
        addMapping("Fisherman", BuildingFisherman.class, BuildingFisherman.View.class, BlockHutFisherman.class);
        addMapping("GuardTower", BuildingGuardTower.class, BuildingGuardTower.View.class, BlockHutGuardTower.class);
        addMapping("WareHouse", BuildingWareHouse.class, BuildingWareHouse.View.class, BlockHutWareHouse.class);
    }
    /**
     * A list which contains the position of all containers which belong to the worker building.
     */
    private final List<BlockPos> containerList = new ArrayList<>();
    /**
     * The location of the building.
     */
    private final BlockPos                 location;
    /**
     * The colony the building belongs to.
     */
    @NotNull
    private final Colony colony;
    /**
     * The tileEntity of the building.
     */
    private       TileEntityColonyBuilding tileEntity;

    /**
     * The level of the building.
     */
    private int buildingLevel = 0;

    /**
     * The rotation of the building.
     */
    private int rotation = 0;

    /**
     * The building style.
     */
    private String style = "wooden";

    /**
     * Made to check if the building has to update the server/client.
     */
    private boolean dirty = false;

    /**
     * Constructor for a AbstractBuilding.
     *
     * @param colony Colony the building belongs to.
     * @param pos    Location of the building (it's Hut Block).
     */
    protected AbstractBuilding(@NotNull final Colony colony, final BlockPos pos)
    {
        location = pos;
        this.colony = colony;
    }

    /**
     * Add build to a mapping.
     * <code>buildingClass</code> needs to extend {@link AbstractBuilding}.
     * <code>parentBlock</code> needs to extend {@link AbstractBlockHut}.
     *
     * @param name          name of building.
     * @param buildingClass subclass of AbstractBuilding, located in {@link com.minecolonies.coremod.colony.buildings}.
     * @param viewClass     subclass of AbstractBuilding.View.
     * @param parentBlock   subclass of Block, located in {@link com.minecolonies.coremod.blocks}.
     */
    private static void addMapping(
            final String name,
            @NotNull final Class<? extends AbstractBuilding> buildingClass,
            @NotNull final Class<? extends AbstractBuilding.View> viewClass,
            @NotNull final Class<? extends AbstractBlockHut> parentBlock)
    {
        final int buildingHashCode = buildingClass.getName().hashCode();

        if (nameToClassMap.containsKey(name) || classNameHashToViewClassMap.containsKey(buildingHashCode))
        {
            throw new IllegalArgumentException("Duplicate type '" + name + "' when adding AbstractBuilding class mapping");
        }
        else
        {
            try
            {
                /*
                If a constructor exist for the building, put the building in the lists.
                 */
                if (buildingClass.getDeclaredConstructor(Colony.class, BlockPos.class) != null)
                {
                    nameToClassMap.put(name, buildingClass);
                    classToNameMap.put(buildingClass, name);
                    classNameHashToViewClassMap.put(buildingHashCode, viewClass);
                }
            }
            catch (final NoSuchMethodException exception)
            {
                throw new IllegalArgumentException("Missing constructor for type '" + name + "' when adding AbstractBuilding class mapping", exception);
            }
        }

        if (blockClassToBuildingClassMap.containsKey(parentBlock))
        {
            throw new IllegalArgumentException("AbstractBuilding type '" + name + "' uses TileEntity '" + parentBlock.getClass().getName() + "' which is already in use.");
        }
        else
        {
            blockClassToBuildingClassMap.put(parentBlock, buildingClass);
        }
    }

    /**
     * Create and load a AbstractBuilding given it's saved NBTTagCompound.
     * Calls {@link #readFromNBT(net.minecraft.nbt.NBTTagCompound)}.
     *
     * @param colony   The owning colony.
     * @param compound The saved data.
     * @return {@link AbstractBuilding} created from the compound.
     */
    @Nullable
    public static AbstractBuilding createFromNBT(final Colony colony, @NotNull final NBTTagCompound compound)
    {
        @Nullable AbstractBuilding building = null;
        @Nullable Class<?> oclass = null;

        try
        {
            oclass = nameToClassMap.get(compound.getString(TAG_BUILDING_TYPE));

            if (oclass != null)
            {
                @NotNull final BlockPos pos = BlockPosUtil.readFromNBT(compound, TAG_LOCATION);
                final Constructor<?> constructor = oclass.getDeclaredConstructor(Colony.class, BlockPos.class);
                building = (AbstractBuilding) constructor.newInstance(colony, pos);
            }
        }
        catch (@NotNull NoSuchMethodException | InstantiationException | InvocationTargetException | IllegalAccessException exception)
        {
            Log.getLogger().error(exception);
        }

        if (building == null)
        {
            Log.getLogger().warn(String.format("Unknown Building type '%s' or missing constructor of proper format.", compound.getString(TAG_BUILDING_TYPE)));
            return null;
        }

        try
        {
            building.readFromNBT(compound);
        }
        catch (final RuntimeException ex)
        {
            Log.getLogger().error(String.format("A Building %s(%s) has thrown an exception during loading, its state cannot be restored. Report this to the mod author",
                    compound.getString(TAG_BUILDING_TYPE), oclass.getName()), ex);
            building = null;
        }

        return building;
    }

    /**
     * Load data from NBT compound.
     * Writes to {@link #buildingLevel}, {@link #rotation} and {@link #style}.
     *
     * @param compound {@link net.minecraft.nbt.NBTTagCompound} to read data from.
     */
    public void readFromNBT(@NotNull final NBTTagCompound compound)
    {
        buildingLevel = compound.getInteger(TAG_BUILDING_LEVEL);

        rotation = compound.getInteger(TAG_ROTATION);
        style = compound.getString(TAG_STYLE);
        if (style.isEmpty())
        {
            Log.getLogger().warn("Loaded empty style, setting to wooden");
            style = "wooden";
        }

        final NBTTagList containerTagList = compound.getTagList(TAG_CONTAINERS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < containerTagList.tagCount(); ++i)
        {
            final NBTTagCompound containerCompound = containerTagList.getCompoundTagAt(i);
            containerList.add(NBTUtil.getPosFromTag(containerCompound));
        }
    }

    /**
     * Create a Building given it's TileEntity.
     *
     * @param colony The owning colony.
     * @param parent The Tile Entity the building belongs to.
     * @return {@link AbstractBuilding} instance, without NBTTags applied.
     */
    @Nullable
    public static AbstractBuilding create(final Colony colony, @NotNull final TileEntityColonyBuilding parent)
    {
        @Nullable AbstractBuilding building = null;
        final Class<?> oclass;

        try
        {
            oclass = blockClassToBuildingClassMap.get(parent.getBlockType().getClass());

            if (oclass == null)
            {
                Log.getLogger().error(String.format("TileEntity %s does not have an associated Building.", parent.getClass().getName()));
                return null;
            }

            final BlockPos loc = parent.getPosition();
            final Constructor<?> constructor = oclass.getDeclaredConstructor(Colony.class, BlockPos.class);
            building = (AbstractBuilding) constructor.newInstance(colony, loc);
        }
        catch (@NotNull NoSuchMethodException | InstantiationException | InvocationTargetException | IllegalAccessException exception)
        {
            Log.getLogger().error(String.format("Unknown Building type '%s' or missing constructor of proper format.", parent.getClass().getName()), exception);
        }

        return building;
    }

    /**
     * Create a AbstractBuilding View given it's saved NBTTagCompound.
     *
     * @param colony The owning colony.
     * @param id     Chunk coordinate of the block a view is created for.
     * @param buf    The network data.
     * @return {@link AbstractBuilding.View} created from reading the buf.
     */
    @Nullable
    public static View createBuildingView(final ColonyView colony, final BlockPos id, @NotNull final ByteBuf buf)
    {
        @Nullable View view = null;
        @Nullable Class<?> oclass = null;

        try
        {
            final int typeHash = buf.readInt();
            oclass = classNameHashToViewClassMap.get(typeHash);

            if (oclass != null)
            {
                final Constructor<?> constructor = oclass.getDeclaredConstructor(ColonyView.class, BlockPos.class);
                view = (View) constructor.newInstance(colony, id);
            }
        }
        catch (@NotNull NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException exception)
        {
            Log.getLogger().error(exception);
        }

        if (view == null)
        {
            Log.getLogger().warn("Unknown AbstractBuilding type, missing View subclass, or missing constructor of proper format.");
            return null;
        }

        try
        {
            view.deserialize(buf);
        }
        catch (final IndexOutOfBoundsException ex)
        {
            Log.getLogger().error(
                    String.format("A AbstractBuilding View (%s) has thrown an exception during deserializing, its state cannot be restored. Report this to the mod author",
                            oclass.getName()), ex);
            return null;
        }

        return view;
    }

    /**
     * Children must return the name of their structure.
     *
     * @return StructureProxy name.
     */
    public abstract String getSchematicName();

    /**
     * Checks if a block matches the current object.
     *
     * @param block Block you want to know whether it matches this class or not.
     * @return True if the block matches this class, otherwise false.
     */
    public boolean isMatchingBlock(@NotNull final Block block)
    {
        final Class<?> c = blockClassToBuildingClassMap.get(block.getClass());
        return getClass().equals(c);
    }

    /**
     * Save data to NBT compound.
     * Writes the {@link #buildingLevel}, {@link #rotation}, {@link #style}, {@link #location}, and {@link #getClass()} value.
     *
     * @param compound {@link net.minecraft.nbt.NBTTagCompound} to write data to.
     */
    public void writeToNBT(@NotNull final NBTTagCompound compound)
    {
        final String s = classToNameMap.get(this.getClass());

        if (s == null)
        {
            throw new IllegalStateException(this.getClass() + " is missing a mapping! This is a bug!");
        }
        else
        {
            compound.setString(TAG_BUILDING_TYPE, s);
            BlockPosUtil.writeToNBT(compound, TAG_LOCATION, location);
        }

        compound.setInteger(TAG_BUILDING_LEVEL, buildingLevel);
        compound.setInteger(TAG_ROTATION, rotation);
        compound.setString(TAG_STYLE, style);


        @NotNull final NBTTagList containerTagList = new NBTTagList();
        for (@NotNull final BlockPos pos : containerList)
        {
            containerTagList.appendTag(NBTUtil.createPosTag(pos));
        }
        compound.setTag(TAG_CONTAINERS, containerTagList);
    }

    /**
     * Returns the {@link BlockPos} of the current object, also used as ID.
     *
     * @return {@link BlockPos} of the current object.
     */
    @Override
    public BlockPos getLocation()
    {
        return location;
    }

    /**
     * Returns whether the instance is dirty or not.
     *
     * @return true if dirty, false if not.
     */
    public final boolean isDirty()
    {
        return dirty;
    }

    /**
     * Sets {@link #dirty} to false, meaning that the instance is up to date.
     */
    public final void clearDirty()
    {
        dirty = false;
    }

    /**
     * Destroys the block.
     * Calls {@link #onDestroyed()}.
     */
    public final void destroy()
    {
        onDestroyed();
        colony.removeBuilding(this);
    }

    /**
     * Method to do things when a block is destroyed.
     */
    public void onDestroyed()
    {
        final TileEntityColonyBuilding tileEntityNew = this.getTileEntity();
        final World world = colony.getWorld();
        final Block block = world.getBlockState(this.location).getBlock();

        if (tileEntityNew != null)
        {
            InventoryHelper.dropInventoryItems(world, this.location, (IInventory) tileEntityNew);
            world.updateComparatorOutputLevel(this.location, block);
        }
        ConstructionTapeHelper.removeConstructionTape(this, world);
    }

    /**
     * Returns the tile entity that belongs to the colony building.
     *
     * @return {@link TileEntityColonyBuilding} object of the building.
     */
    public TileEntityColonyBuilding getTileEntity()
    {
        if ((tileEntity == null || tileEntity.isInvalid()) && colony.getWorld().getBlockState(location).getBlock() != null)
        {
            final TileEntity te = getColony().getWorld().getTileEntity(location);
            if (te instanceof TileEntityColonyBuilding)
            {
                tileEntity = (TileEntityColonyBuilding) te;
                if (tileEntity.getBuilding() == null)
                {
                    tileEntity.setColony(colony);
                    tileEntity.setBuilding(this);
                }
            }
        }

        return tileEntity;
    }

    /**
     * Sets the tile entity for the building.
     *
     * @param te {@link TileEntityColonyBuilding} that will fill the {@link #tileEntity} field.
     */
    public void setTileEntity(final TileEntityColonyBuilding te)
    {
        tileEntity = te;
    }

    /**
     * Returns the colony of the building.
     *
     * @return {@link com.minecolonies.coremod.colony.Colony} of the current object.
     */
    @Override
    @NotNull
    public Colony getColony()
    {
        return colony;
    }

    /**
     * Method to remove a citizen.
     *
     * @param citizen Citizen to be removed.
     */
    public void removeCitizen(final CitizenData citizen)
    {
        // Can be overridden by other buildings.
    }

    /**
     * On tick of the server.
     *
     * @param event {@link net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent}
     */
    public void onServerTick(final TickEvent.ServerTickEvent event)
    {
        // Can be overridden by other buildings.
    }

    /**
     * On tick of the world.
     *
     * @param event {@link net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent}
     */
    public void onWorldTick(final TickEvent.WorldTickEvent event)
    {
        // Can be overridden by other buildings.
    }

    /**
     * Requests an upgrade for the current building.
     */
    public void requestUpgrade()
    {
        if (buildingLevel < getMaxBuildingLevel())
        {
            requestWorkOrder(buildingLevel + 1);
        }
    }

    /**
     * Get the current level of the work order.
     *
     * @return NO_WORK_ORDER if not current work otherwise the level requested.
     */
    private int getCurrentWorkOrderLevel()
    {
        for (@NotNull final WorkOrderBuild o : colony.getWorkManager().getWorkOrdersOfType(WorkOrderBuild.class))
        {
            if (o.getBuildingLocation().equals(getID()))
            {
                return o.getUpgradeLevel();
            }
        }

        return NO_WORK_ORDER;
    }

    /**
     * Checks if this building have a work order.
     *
     * @return true if the building is building, upgrading or repairing.
     */
    @Override
    public boolean hasWorkOrder()
    {
        return getCurrentWorkOrderLevel() != NO_WORK_ORDER;
    }

    /**
     * Adds work orders to the {@link Colony#workManager}.
     *
     * @param level Desired level.
     */
    private void requestWorkOrder(final int level)
    {
        for (@NotNull final WorkOrderBuild o : colony.getWorkManager().getWorkOrdersOfType(WorkOrderBuild.class))
        {
            if (o.getBuildingLocation().equals(getID()))
            {
                return;
            }
        }

        colony.getWorkManager().addWorkOrder(new WorkOrderBuild(this, level));
        LanguageHandler.sendPlayersMessage(colony.getMessageEntityPlayers(), "com.minecolonies.coremod.workOrderAdded");
        markDirty();
    }

    /**
     * Returns the {@link BlockPos} of the current object, also used as ID.
     *
     * @return {@link BlockPos} of the current object.
     */
    @Override
    public BlockPos getID()
    {
        // Location doubles as ID.
        return location;
    }

    /**
     * Requests a repair for the current building.
     */
    public void requestRepair()
    {
        if (buildingLevel > 0)
        {
            requestWorkOrder(buildingLevel);
        }
    }

    /**
     * Remove the work order for the building.
     * <p>
     * Remove either the upgrade or repair work order
     */
    public void removeWorkOrder()
    {
        for (@NotNull final WorkOrderBuild o : colony.getWorkManager().getWorkOrdersOfType(WorkOrderBuild.class))
        {
            if (o.getBuildingLocation().equals(getID()))
            {
                colony.getWorkManager().removeWorkOrder(o.getID());
                markDirty();
                return;
            }
        }
    }

    /**
     * Returns the rotation of the current building.
     *
     * @return integer value of the rotation.
     */
    public int getRotation()
    {
        return rotation;
    }

    /**
     * Sets the rotation of the current building.
     *
     * @param rotation integer value of the rotation.
     */
    public void setRotation(final int rotation)
    {
        this.rotation = rotation;
    }

    /**
     * Returns the style of the current building.
     *
     * @return String representation of the current building-style
     */
    public String getStyle()
    {
        return style;
    }

    /**
     * Sets the style of the building.
     *
     * @param style String value of the style.
     */
    public void setStyle(final String style)
    {
        this.style = style;
    }

    /**
     * Called upon completion of an upgrade process.
     *
     * @param newLevel The new level.
     */
    public void onUpgradeComplete(final int newLevel)
    {
        // Does nothing here
    }

    /**
     * Serializes to view.
     * Sends 3 integers.
     * 1) hashcode of the name of the class.
     * 2) building level.
     * 3) max building level.
     *
     * @param buf ByteBuf to write to.
     */
    public void serializeToView(@NotNull final ByteBuf buf)
    {
        buf.writeInt(this.getClass().getName().hashCode());
        buf.writeInt(getBuildingLevel());
        buf.writeInt(getMaxBuildingLevel());
        buf.writeInt(getCurrentWorkOrderLevel());
    }

    /**
     * Returns the level of the current object.
     *
     * @return Level of the current object.
     */
    @Override
    public int getBuildingLevel()
    {
        return buildingLevel;
    }

    /**
     * Sets the current level of the building.
     *
     * @param level Level of the building.
     */
    public void setBuildingLevel(final int level)
    {
        if (level > getMaxBuildingLevel())
        {
            return;
        }

        buildingLevel = level;
        markDirty();
        ColonyManager.markDirty();
    }

    /**
     * Marks the instance and the building dirty.
     */
    public final void markDirty()
    {
        dirty = true;
        colony.markBuildingsDirty();
    }

    /**
     * Add a new container to the building.
     *
     * @param pos position to add.
     */
    public void addContainerPosition(BlockPos pos)
    {
        containerList.add(pos);
    }

    /**
     * Remove a container from the building.
     *
     * @param pos position to remove.
     */
    public void removeContainerPosition(BlockPos pos)
    {
        containerList.remove(pos);
    }

    /**
     * Get all additional containers which belong to the building.
     *
     * @return a copy of the list to avoid currentModification exception.
     */
    public List<BlockPos> getAdditionalCountainers()
    {
        return new ArrayList<>(containerList);
    }

    //------------------------- Starting Required Tools/Item handling -------------------------//

    /**
     * Override this method if you want to keep an amount of items in inventory.
     * When the inventory is full, everything get's dumped into the building chest.
     * But you can use this method to hold some stacks back.
     *
     * @return a list of objects which should be kept.
     */
    public Map<ItemStorage, Integer> getRequiredItemsAndAmount()
    {
        return Collections.emptyMap();
    }

    /**
     * Method used to check for a given worker if he has open request.
     * @param citizen The worker to check for.
     * @return True when the given worker has open requests, false when not.
     */
    public boolean hasWorkerOpenRequests(CitizenData citizen) {
        return !citizensByRequests.containsKey(citizen.getId()) || citizensByRequests.get(citizen.getId()).isEmpty();
    }

    @NotNull
    public ItemStack getFirstUnpickedDelivery(CitizenData citizen)
    {
        if (!hasWorkerOpenRequests(citizen))
        {
            return ItemStack.EMPTY;
        }
    }

    /**
     * Check if the worker needs anything. Tool or item.
     * Basically checks if the worker has open requests, regardless of by whom they will be fullfilled.
     *
     * @return true if so.
     */
    public boolean needsAnything()
    {
        return !openRequests.isEmpty();
    }

    /**
     * Check if any items are needed at the moment.
     *
     * @return true if so.
     */
    public boolean areItemsNeeded()
    {
        return !openRequests.get(ItemStack.class).isEmpty();
    }

    /**
     * Check if the worker needs a tool of the given type.
     *
     * @param toolClass The type of tool requested.
     * @return True if so.
     */
    public boolean requiresTool(String toolClass) {
        if (openRequests.get(Tool.class).isEmpty())
            return false;

        List<IToken> tokenList = openRequests.get(Tool.class);
        for(IToken token : tokenList) {
            IRequest<Tool> toolIRequest = colony.getRequestManager().getRequestForToken(token);

            if (toolIRequest.getRequest().getToolClass().equals(toolClass))
                return true;
        }

        return false;
    }

    /**
     * Check if the worker requires a shovel.
     *
     * @return true if so.
     */
    public boolean needsShovel() { return requiresTool(Utils.SHOVEL); }

    /**
     * Check if the worker requires a axe.
     *
     * @return true if so.
     */
    public boolean needsAxe()
    {
        return requiresTool(Utils.AXE);
    }

    /**
     * Check if the worker requires a hoe.
     *
     * @return true if so.
     */
    public boolean needsHoe()
    {
        return requiresTool(Utils.HOE);
    }

    /**
     * Check if the worker requires a pickaxe.
     *
     * @return true if so.
     */
    public boolean needsPickaxe()
    {
        return requiresTool(Utils.PICKAXE);
    }

    /**
     * Check if the worker requires a weapon.
     *
     * @return true if so.
     */
    public boolean needsWeapon()
    {
        return requiresTool(Utils.WEAPON);
    }

    @Nullable
    public Tool getRequestedToolForClass(String toolClass) {
        if (!requiresTool(toolClass))
            return null;

        List<IToken> tokenList = openRequests.get(Tool.class);
        for(IToken token : tokenList) {
            IRequest<Tool> toolIRequest = colony.getRequestManager().getRequestForToken(token);

            if (toolIRequest.getRequest().getToolClass().equals(toolClass))
                return toolIRequest.getRequest();
        }

        return null;
    }

    /**
     * Check the required pickaxe level..
     *
     * @return the mining level of the pickaxe.
     */
    public int getNeededPickaxeLevel()
    {
        return getRequestedToolForClass(Utils.PICKAXE).getMinLevel();
    }

    public <Request> void createRequest(@NotNull CitizenData requester, @NotNull Request requested) {
        IToken requestToken = colony.getRequestManager().createAndAssignRequest(requester, requested);

    }

    /**
     * Check for the required tool and return the describing string.
     *
     * @return the string of the required tool.
     */
    public String getRequiredTool()
    {
        if (needsHoe())
        {
            return Utils.HOE;
        }

        if (needsAxe())
        {
            return Utils.AXE;
        }

        if (needsPickaxe())
        {
            return Utils.PICKAXE;
        }

        if (needsShovel())
        {
            return Utils.SHOVEL;
        }

        if (needsWeapon())
        {
            return Utils.WEAPON;
        }

        return "";
    }

    /**
     * Try to transfer a stack to one of the inventories of the building.
     *
     * @param stack the stack to transfer.
     * @param world the world to do it in.
     * @return true if was able to.
     */
    public boolean transferStack(@NotNull final ItemStack stack, @NotNull final World world)
    {
        if (tileEntity == null || InventoryUtils.isInventoryFull(tileEntity))
        {
            for (final BlockPos pos : containerList)
            {
                final TileEntity tempTileEntity = world.getTileEntity(pos);
                if (tempTileEntity instanceof TileEntityChest && !InventoryUtils.isInventoryFull((IInventory) tempTileEntity))
                {
                    return InventoryUtils.addItemStackToInventory((IInventory) tempTileEntity, stack);
                }
            }
        }
        else
        {
            return InventoryUtils.addItemStackToInventory(tileEntity, stack);
        }
        return false;
    }

    /**
     * Try to transfer a stack to one of the inventories of the building and force the transfer.
     *
     * @param stack the stack to transfer.
     * @param world the world to do it in.
     * @return the itemStack which has been replaced
     */
    @Nullable
    public ItemStack forceTransferStack(final ItemStack stack, final World world)
    {
        if (tileEntity == null)
        {
            for (final BlockPos pos : containerList)
            {
                final TileEntity tempTileEntity = world.getTileEntity(pos);
                if (tempTileEntity instanceof TileEntityChest && !InventoryUtils.isInventoryFull((IInventory) tempTileEntity))
                {
                    return InventoryUtils.forceItemStackToInventory((IInventory) tempTileEntity, stack, this);
                }
            }
        }
        else
        {
            return InventoryUtils.forceItemStackToInventory(tileEntity, stack, this);
        }
        return null;
    }

    //------------------------- Ending Required Tools/Item handling -------------------------//

    /**
     * The AbstractBuilding View is the client-side representation of a AbstractBuilding.
     * Views contain the AbstractBuilding's data that is relevant to a Client, in a more client-friendly form.
     * Mutable operations on a View result in a message to the server to perform the operation.
     */
    public static class View implements IBuilding
    {
        private final ColonyView colony;
        @NotNull
        private final BlockPos   location;

        private int buildingLevel    = 0;
        private int buildingMaxLevel = 0;
        private int workOrderLevel   = NO_WORK_ORDER;

        /**
         * Creates a building view.
         *
         * @param c ColonyView the building is in.
         * @param l The location of the building.
         */
        protected View(final ColonyView c, @NotNull final BlockPos l)
        {
            colony = c;
            location = new BlockPos(l);
        }

        /**
         * Gets the id for this building.
         *
         * @return A BlockPos because the building ID is its location.
         */
        @NotNull
        public BlockPos getID()
        {
            // Location doubles as ID
            return location;
        }

        /**
         * Gets the location of this building.
         *
         * @return A BlockPos, where this building is.
         */
        @NotNull
        public BlockPos getLocation()
        {
            return location;
        }

        /**
         * Gets the ColonyView that this building belongs to.
         *
         * @return ColonyView, client side interpretations of Colony.
         */
        public ColonyView getColony()
        {
            return colony;
        }

        /**
         * Get the current level of the building.
         *
         * @return AbstractBuilding current level.
         */
        public int getBuildingLevel()
        {
            return buildingLevel;
        }

        /**
         * Checks if this building is at its max level.
         *
         * @return true if the building is at its max level.
         */
        public boolean isBuildingMaxLevel()
        {
            return buildingLevel >= buildingMaxLevel;
        }

        /**
         * Get the current work order level.
         *
         * @return 0 if none, othewise the current level worked on
         */
        public int getCurrentWorkOrderLevel()
        {
            return workOrderLevel;
        }

        /**
         * Get the current work order level.
         *
         * @return 0 if none, othewise the current level worked on
         */
        public boolean hasWorkOrder()
        {
            return workOrderLevel != NO_WORK_ORDER;
        }

        /**
         * Children must return their max building level.
         *
         * @return Max building level.
         */
        @Override
        public int getMaxBuildingLevel() {
            return buildingMaxLevel;
        }

        public boolean isBuilding()
        {
            return workOrderLevel != NO_WORK_ORDER && workOrderLevel > buildingLevel;
        }

        public boolean isRepairing()
        {
            return workOrderLevel != NO_WORK_ORDER && workOrderLevel == buildingLevel;
        }

        /**
         * Open the associated BlockOut window for this building.
         */
        public void openGui()
        {
            @Nullable final Window window = getWindow();
            if (window != null)
            {
                window.open();
            }
        }

        /**
         * Will return the window if this building has an associated BlockOut window.
         *
         * @return BlockOut window.
         */
        @Nullable
        public Window getWindow()
        {
            return null;
        }

        /**
         * Read this view from a {@link ByteBuf}.
         *
         * @param buf The buffer to read this view from.
         */
        public void deserialize(@NotNull final ByteBuf buf)
        {
            buildingLevel = buf.readInt();
            buildingMaxLevel = buf.readInt();
            workOrderLevel = buf.readInt();
        }
    }
}
