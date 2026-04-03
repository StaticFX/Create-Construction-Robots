package de.devin.cbbees.content.backpack

import com.simibubi.create.AllFluids
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.compat.HoneyFuelHelper
import net.minecraft.world.item.ItemStack
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction
import net.minecraftforge.fluids.capability.IFluidHandlerItem

/**
 * Forge 1.20.1 fluid handler — uses NBT-based [HoneyFuelHelper] for fuel storage.
 *
 * Conversion: 250mB honey (1 bottle) = [CBBeesConfig.honeyBottleFuelValue] fuel units.
 */
class PortableBeehiveFluidHandler(private val stack: ItemStack) : IFluidHandlerItem {

    companion object {
        const val MB_PER_BOTTLE = 250
    }

    private fun currentFuel(): Int = HoneyFuelHelper.get(stack)
    private fun maxFuel(): Int = CBBeesConfig.portableMaxHoney.get()
    private fun fuelPerBottle(): Int = CBBeesConfig.honeyBottleFuelValue.get()

    private fun remainingCapacityMb(): Int {
        val fuelSpace = maxFuel() - currentFuel()
        if (fuelSpace <= 0) return 0
        return (fuelSpace.toLong() * MB_PER_BOTTLE / fuelPerBottle()).toInt()
    }

    private fun capacityMb(): Int {
        return (maxFuel().toLong() * MB_PER_BOTTLE / fuelPerBottle()).toInt()
    }

    private fun currentMb(): Int {
        return (currentFuel().toLong() * MB_PER_BOTTLE / fuelPerBottle()).toInt()
    }

    private fun isHoney(fluid: FluidStack): Boolean {
        return fluid.fluid.isSame(AllFluids.HONEY.get().source)
    }

    override fun getTanks(): Int = 1

    override fun getFluidInTank(tank: Int): FluidStack {
        val mb = currentMb()
        if (mb <= 0) return FluidStack.EMPTY
        return FluidStack(AllFluids.HONEY.get().source, mb)
    }

    override fun getTankCapacity(tank: Int): Int = capacityMb()

    override fun isFluidValid(tank: Int, stack: FluidStack): Boolean = isHoney(stack)

    override fun fill(resource: FluidStack, action: FluidAction): Int {
        if (resource.isEmpty || !isHoney(resource)) return 0

        val spaceInMb = remainingCapacityMb()
        if (spaceInMb <= 0) return 0

        val toFillMb = minOf(resource.amount, spaceInMb)
        if (toFillMb <= 0) return 0

        if (action.execute()) {
            val fuelGain = (toFillMb.toLong() * fuelPerBottle() / MB_PER_BOTTLE).toInt()
            val newFuel = minOf(currentFuel() + fuelGain, maxFuel())
            HoneyFuelHelper.set(stack, newFuel)
        }

        return toFillMb
    }

    override fun drain(resource: FluidStack, action: FluidAction): FluidStack = FluidStack.EMPTY

    override fun drain(maxDrain: Int, action: FluidAction): FluidStack = FluidStack.EMPTY

    override fun getContainer(): ItemStack = stack
}
