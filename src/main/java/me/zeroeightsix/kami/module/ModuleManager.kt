package me.zeroeightsix.kami.module

import me.zeroeightsix.kami.KamiMod
import me.zeroeightsix.kami.event.events.RenderEvent
import me.zeroeightsix.kami.module.modules.ClickGUI
import me.zeroeightsix.kami.util.ClassFinder
import me.zeroeightsix.kami.util.EntityUtils.getInterpolatedPos
import me.zeroeightsix.kami.util.TimerUtils
import me.zeroeightsix.kami.util.graphics.KamiTessellator
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraftforge.client.event.RenderWorldLastEvent
import java.lang.reflect.InvocationTargetException
import java.util.*

/**
 * Created by 086 on 23/08/2017.
 * Updated by Sasha
 * Updated by Xiaro on 18/08/20
 */
@Suppress("UNCHECKED_CAST")
object ModuleManager {
    private val mc = Minecraft.getMinecraft()

    /** Thread for scanning module during Forge pre-init */
    private var preLoadingThread: Thread? = null

    /** List for module classes found during pre-loading */
    private var moduleClassList: Array<Class<out Module>>? = null

    /** HashMap for the registered Modules */
    private val moduleMap = HashMap<Class<out Module>, Module>()

    /** Array for the registered Modules (sorted) */
    private lateinit var moduleList: Array<Module>

    @JvmStatic
    fun preLoad() {
        preLoadingThread = Thread {
            moduleClassList = ClassFinder.findClasses(ClickGUI::class.java.getPackage().name, Module::class.java)
            KamiMod.log.info("${moduleClassList!!.size} modules found")
        }
        preLoadingThread!!.name = "Modules Pre-Loading"
        preLoadingThread!!.start()
    }

    /**
     * Registers modules
     */
    @JvmStatic
    fun load() {
        preLoadingThread!!.join()
        val stopTimer = TimerUtils.StopTimer()
        for (clazz in moduleClassList!!) {
            try {
                val module = clazz.getConstructor().newInstance() as Module
                moduleMap[module.javaClass] = module
            } catch (e: InvocationTargetException) {
                e.cause!!.printStackTrace()
                System.err.println("Couldn't initiate module " + clazz.simpleName + "! Err: " + e.javaClass.simpleName + ", message: " + e.message)
            } catch (e: Exception) {
                e.printStackTrace()
                System.err.println("Couldn't initiate module " + clazz.simpleName + "! Err: " + e.javaClass.simpleName + ", message: " + e.message)
            }
        }
        initSortedList()
        val time = stopTimer.stop()
        KamiMod.log.info("${moduleMap.size} modules loaded, took ${time}ms")

        /* Clean up variables used during pre-loading and registering */
        preLoadingThread = null
        moduleClassList = null
    }

    private fun initSortedList() {
        Thread {
            moduleList = moduleMap.values.stream().sorted(Comparator.comparing { module: Module ->
                module.javaClass.simpleName
            }).toArray { size -> arrayOfNulls<Module>(size) }
        }.start()
    }

    fun onUpdate() {
        for (module in moduleList) {
            if (isModuleListening(module)) module.onUpdate()
        }
    }

    fun onRender() {
        for (module in moduleList) {
            if (isModuleListening(module)) module.onRender()
        }
    }

    fun onWorldRender(event: RenderWorldLastEvent) {
        mc.profiler.startSection("kami")
        mc.profiler.startSection("setup")
        KamiTessellator.prepareGL()
        GlStateManager.glLineWidth(1f)
        val renderPos = getInterpolatedPos(mc.renderViewEntity!!, event.partialTicks)
        val e = RenderEvent(KamiTessellator, renderPos)
        e.resetTranslation()
        mc.profiler.endSection()
        for (module in moduleList) {
            if (isModuleListening(module)) {
                KamiTessellator.prepareGL()
                module.onWorldRender(e)
                KamiTessellator.releaseGL()
                mc.profiler.endSection()
            }
        }
        mc.profiler.startSection("release")
        GlStateManager.glLineWidth(1f)
        KamiTessellator.releaseGL()
        mc.profiler.endSection()
    }

    fun onBind(eventKey: Int) {
        if (eventKey == 0) return  // if key is the 'none' key (stuff like mod key in i3 might return 0)
        for (module in moduleList) {
            if (module.bind.value.isDown(eventKey)) module.toggle()
        }
    }

    @JvmStatic
    fun getModules(): Array<Module> {
        return moduleList
    }

    @JvmStatic
    fun getModule(clazz: Class<out Module>): Module {
        return moduleMap[clazz] ?: throw(ModuleNotFoundException(clazz.simpleName))
    }

    /**
     * Get typed module object so that no casting is needed afterwards.
     *
     * @param clazz Module class
     * @param [T] Type of module
     * @return Object <[T]>
     **/
    @JvmStatic
    fun <T : Module> getModuleT(clazz: Class<T>): T? {
        return getModule(clazz) as? T?
    }

    @Deprecated("Use `getModule(Class<? extends Module>)` instead")
    @JvmStatic
    fun getModule(name: String?): Module? {
        for (module in moduleMap.entries) {
            if (module.javaClass.simpleName.equals(name, ignoreCase = true) || module.value.originalName.equals(name, ignoreCase = true)) {
                return module.value
            }
        }
        throw ModuleNotFoundException("Error: Module not found. Check the spelling of the module. (getModuleByName(String) failed)")
    }

    @JvmStatic
    fun isModuleEnabled(clazz: Class<out Module>): Boolean {
        return getModule(clazz)?.isEnabled ?: false
    }

    @JvmStatic
    fun isModuleListening(clazz: Class<out Module>): Boolean {
        val module = getModule(clazz) ?: return false
        return isModuleListening(module)
    }

    @JvmStatic
    fun isModuleListening(module: Module): Boolean {
        return module.isEnabled || module.alwaysListening
    }

    class ModuleNotFoundException(s: String?) : IllegalArgumentException(s)
}