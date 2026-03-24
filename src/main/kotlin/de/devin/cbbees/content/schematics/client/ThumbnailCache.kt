package de.devin.cbbees.content.schematics.client

import com.mojang.blaze3d.platform.NativeImage
import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream

/**
 * Asynchronously downloads and caches schematic thumbnail images as [DynamicTexture]s.
 * Returns a [ResourceLocation] that can be used with [net.minecraft.client.gui.GuiGraphics.blit].
 *
 * Supports PNG, JPEG, BMP, GIF (via [ImageIO]) and WebP (via TwelveMonkeys, instantiated directly
 * to avoid SPI discovery issues in NeoForge's classloader).
 */
@OnlyIn(Dist.CLIENT)
object ThumbnailCache {

    data class CachedThumb(val location: ResourceLocation, val width: Int, val height: Int)

    private val webpSpi = WebPImageReaderSpi()
    private val textures = mutableMapOf<String, CachedThumb>()
    private val dynamicTextures = mutableMapOf<String, DynamicTexture>()
    private val loading = mutableSetOf<String>()
    private val failed = mutableSetOf<String>()
    private val client = HttpClient.newHttpClient()

    fun get(url: String): CachedThumb? = textures[url]
    fun isLoading(url: String): Boolean = url in loading

    fun load(url: String) {
        if (url in textures || url in loading || url in failed) return
        loading.add(url)

        CompletableFuture.supplyAsync {
            val request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
            if (response.statusCode() != 200) throw RuntimeException("HTTP ${response.statusCode()}")
            response.body()
        }.thenApplyAsync { bytes ->
            toNativeImage(decodeImage(bytes))
        }.thenAccept { image ->
            Minecraft.getInstance().execute {
                try {
                    val texture = DynamicTexture(image)
                    val id = ResourceLocation.fromNamespaceAndPath("cbbees", "thumb/${url.hashCode().toUInt()}")
                    Minecraft.getInstance().textureManager.register(id, texture)
                    textures[url] = CachedThumb(id, image.width, image.height)
                    dynamicTextures[url] = texture
                } catch (_: Exception) {
                    failed.add(url)
                }
                loading.remove(url)
            }
        }.exceptionally {
            Minecraft.getInstance().execute {
                loading.remove(url)
                failed.add(url)
            }
            null
        }
    }

    /**
     * Decodes image bytes. Checks for WebP magic bytes (RIFF) and uses the TwelveMonkeys
     * WebP reader directly; falls back to [ImageIO] for all other formats.
     */
    private fun decodeImage(bytes: ByteArray): BufferedImage {
        // WebP: starts with RIFF....WEBP
        val isWebP = bytes.size >= 12
                && bytes[0] == 0x52.toByte() // R
                && bytes[1] == 0x49.toByte() // I
                && bytes[2] == 0x46.toByte() // F
                && bytes[3] == 0x46.toByte() // F

        if (isWebP) {
            val reader = webpSpi.createReaderInstance()
            try {
                val stream = MemoryCacheImageInputStream(ByteArrayInputStream(bytes))
                reader.input = stream
                return reader.read(0)
            } finally {
                reader.dispose()
            }
        }

        return ImageIO.read(ByteArrayInputStream(bytes))
            ?: throw RuntimeException("Unsupported image format (${bytes.size} bytes, magic: ${bytes.take(4).joinToString(" ") { "%02X".format(it) }})")
    }

    /**
     * Converts a [BufferedImage] to a [NativeImage] for Minecraft's renderer.
     * Reads ARGB pixels and writes them as ABGR (the format [NativeImage] expects).
     */
    private fun toNativeImage(buffered: BufferedImage): NativeImage {
        val w = buffered.width
        val h = buffered.height
        val image = NativeImage(NativeImage.Format.RGBA, w, h, false)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val argb = buffered.getRGB(x, y)
                val a = (argb shr 24) and 0xFF
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                // NativeImage pixel format: 0xAABBGGRR
                image.setPixelRGBA(x, y, (a shl 24) or (b shl 16) or (g shl 8) or r)
            }
        }
        return image
    }

    fun clear() {
        for (tex in dynamicTextures.values) tex.close()
        dynamicTextures.clear()
        textures.clear()
        loading.clear()
        failed.clear()
    }
}
