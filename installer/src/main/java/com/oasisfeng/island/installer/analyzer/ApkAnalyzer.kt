package com.oasisfeng.island.installer.analyzer

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInfo.INSTALL_LOCATION_AUTO
import android.content.pm.PackageInfo.INSTALL_LOCATION_INTERNAL_ONLY
import android.content.pm.PackageInfo.INSTALL_LOCATION_PREFER_EXTERNAL
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.P
import android.os.Build.VERSION_CODES.Q
import android.util.Log
import androidx.annotation.WorkerThread
import com.jaredrummler.apkparser.parser.*
import com.jaredrummler.apkparser.struct.AndroidConstants
import com.jaredrummler.apkparser.struct.xml.XmlNodeStartTag
import com.oasisfeng.island.installer.AppInstallerUtils.setRequestedLegacyExternalStorage
import com.oasisfeng.java.utils.IoUtils
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis

object ApkAnalyzer {

	@JvmStatic fun analyzeAsync(context: Context, input: InputStream, callback: (PackageInfo?) -> Unit) {
		thread(start = true, name = TAG) { callback(analyze(context, input)) }
	}

	@WorkerThread private fun analyze(context: Context, input: InputStream): PackageInfo? {
		return try { parse(context, input) }
		catch (e: Exception) { null.also { Log.w(TAG, "Error analyzing APK", e) }}
		finally { IoUtils.closeQuietly(input) }
	}

	@Throws(IOException::class) @WorkerThread private fun parse(context: Context, input: InputStream): PackageInfo? {
		var resources: ByteBuffer? = null; var manifest: ByteBuffer? = null
		val zipInput = ZipInputStream(input)
		do {
			val start = System.currentTimeMillis()
			val entry = zipInput.nextEntry ?: break
			Log.d(TAG, "${System.currentTimeMillis() - start}ms spent inflating ${entry.name}")
			if (entry.name == AndroidConstants.RESOURCE_FILE)
				resources = zipInput.readAllToByteBuffer(entry.size)
			else if (entry.name == AndroidConstants.MANIFEST_FILE)
				manifest = zipInput.readAllToByteBuffer(entry.size)
		} while (resources == null || manifest == null)
		if (manifest == null) return null

		val resourceTableParser = ResourceTableParser(resources).apply {
			val elapsed = measureTimeMillis { parse() }
			Log.d(TAG, "${elapsed}ms spent parsing resource table.") }
//		locales = resourceTableParser.locales
		var requestLegacyExternalStorage = false; var split: String? = null
		val translator = object: ApkMetaTranslator() {
			override fun onStartTag(xmlNodeStartTag: XmlNodeStartTag) {
				super.onStartTag(xmlNodeStartTag)
				if (xmlNodeStartTag.name == "manifest")
					split = xmlNodeStartTag.attributes.get("split")
				if (SDK_INT == Q && xmlNodeStartTag.name == "application")
					requestLegacyExternalStorage = xmlNodeStartTag.attributes.getBoolean("requestLegacyExternalStorage", false)
			}
		}
		BinaryXmlParser(manifest, resourceTableParser.resourceTable).apply {
			locale = context.resources.configuration.locales.get(0)
			xmlStreamer = CompositeXmlStreamer(XmlTranslator(), translator)
			val elapsed = measureTimeMillis { parse() }
			Log.d(TAG, "${elapsed}ms spent parsing AndroidManifest.") }
		val meta = translator.apkMeta

		return PackageInfo().apply {
			packageName = meta.packageName
			splitNames = split?.let { arrayOf(it) }
			versionName = meta.versionName; @Suppress("DEPRECATION")
			versionCode = meta.versionCode.toInt()
			if (SDK_INT >= P) longVersionCode = meta.versionCode
			applicationInfo = ApplicationInfo().apply {
				packageName = meta.packageName
				nonLocalizedLabel = meta.label ?: meta.packageName
				installLocation = when(meta.installLocation) {
					"internalOnly" -> INSTALL_LOCATION_INTERNAL_ONLY; "preferExternal" -> INSTALL_LOCATION_PREFER_EXTERNAL
					else -> INSTALL_LOCATION_AUTO }
				minSdkVersion = try { meta.minSdkVersion.toInt() } catch (e: NumberFormatException) { 0 }
				targetSdkVersion = try { Integer.parseInt(meta.targetSdkVersion) } catch (e: NumberFormatException) { 0 }
				requestedPermissions = meta.usesPermissions.takeIf { it.isNotEmpty() }?.toTypedArray()
				if (SDK_INT == Q && requestLegacyExternalStorage) setRequestedLegacyExternalStorage() }}
	}

	@Throws(IOException::class) private fun InputStream.readAllToByteBuffer(sizeHint: Long): ByteBuffer {
		val buf = ByteArray(1024)
		return ByteArrayOutputStream(sizeHint.toInt()).use { output ->
			var len: Int; while (read(buf).also { len = it } != -1) output.write(buf, 0, len)
			ByteBuffer.wrap(output.toByteArray()) }
	}

	private const val TAG = "Island.AA"
}
