package com.oasisfeng.island.shuttle

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import java.lang.reflect.Modifier

private typealias CtxFun<R> = Context.() -> R

internal class Closure(private val functionClass: Class<CtxFun<*>>, private val variables: Array<Any?>): Parcelable {

	fun invoke(context: Context): Any? {
		val constructor = functionClass.declaredConstructors[0].apply { isAccessible = true }
		val args: Array<Any?> = constructor.parameterTypes.map(::getDefaultValue).toTypedArray()
		val variables = variables
		@Suppress("UNCHECKED_CAST") val block = constructor.newInstance(* args) as CtxFun<*>
		block.javaClass.getMemberFields().forEachIndexed { index, field ->  // Constructor arguments do not matter, as all fields are replaced.
			field.set(block, when (field.type) {
				Context::class.java -> context
				Closure::class.java -> (variables[index] as? Closure)?.invoke(context)
				else -> variables[index] }) }
		return block(context)
	}

	constructor(procedure: CtxFun<*>): this(procedure.javaClass, extractVariablesFromFields(procedure)) {
		val constructors = javaClass.declaredConstructors
		require(constructors.isNotEmpty()) { "The method must have at least one constructor" }
	}

	private fun getDefaultValue(type: Class<*>)
			= if (type.isPrimitive) java.lang.reflect.Array.get(java.lang.reflect.Array.newInstance(type, 1), 0) else null

	override fun toString() = "Closure{${functionClass.name}}"

	override fun describeContents() = 0
	override fun writeToParcel(dest: Parcel, flags: Int) =
			dest.run { writeString(functionClass.name); writeArray(variables) }
	@Suppress("UNCHECKED_CAST") constructor(parcel: Parcel, cl: ClassLoader)
			: this(cl.loadClass(parcel.readString()) as Class<CtxFun<*>>, parcel.readArray(cl)!!)

	companion object CREATOR : Parcelable.ClassLoaderCreator<Closure> {

		override fun createFromParcel(parcel: Parcel, classLoader: ClassLoader) = Closure(parcel, classLoader)
		override fun createFromParcel(parcel: Parcel) = Closure(parcel, Closure::class.java.classLoader!!)
		override fun newArray(size: Int): Array<Closure?> = arrayOfNulls(size)

		// Automatically generated fields for captured variables, by compiler (indeterminate order)
		private fun extractVariablesFromFields(procedure: CtxFun<*>)
				= procedure.javaClass.getMemberFields().map { wrapIfNeeded(it.get(procedure)) }.toTypedArray()

		private fun <T> Class<T>.getMemberFields()
				= declaredFields.filter { if (Modifier.isStatic(it.modifiers)) false else { it.isAccessible = true; true }}

		private fun wrapIfNeeded(obj: Any?): Any? = if (obj is Context) null else obj
	}
}
