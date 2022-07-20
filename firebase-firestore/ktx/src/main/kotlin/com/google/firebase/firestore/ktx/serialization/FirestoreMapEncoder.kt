// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.firestore.ktx.serialization

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.encoding.FirestoreAbstractEncoder
import com.google.firebase.firestore.encoding.FirestoreSerializersModule
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.LongAsStringSerializer.descriptor
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

/**
 * The entry point of Firestore Kotlin Serialization Process. It encodes the @[Serializable] objects
 * into nested maps of Firestore supported types.
 *
 * For a @[Serializable] object:
 * - At compile time, a serializer will be generated by the Kotlin serialization compiler plugin (or
 * a custom serializer can be passed in). The structure information of the @[Serializable] object
 * will be recorded inside of the serializer’s descriptor (i.e. the name/type of each property to be
 * encoded, the annotation on each property).
 * - At runtime, during the encoding process, based on the descriptor’s information, a nested map
 * will be generated. Each property which has its own structure (i.e. a nested @[Serializable]
 * object, a nested list) will be encoded as an embedded map nested inside.
 * - At the end of the encoding process, the filled nested map be returned as the encoding result of
 * the @[Serializable] object.
 *
 * @param descriptor: The [SerialDescriptor] of the encoding object.
 * @param depth Object depth, defined as objects within objects.
 * @param callback Sends the encoded nested map back to the caller.
 */
private class FirestoreMapEncoder(
    private val descriptor: SerialDescriptor,
    private val depth: Int = 0,
    private val callback: (MutableMap<String, Any?>) -> Unit
) : FirestoreAbstractEncoder, AbstractEncoder() {

    /** A map that saves the encoding result. */
    private val encodedMap: MutableMap<String, Any?> = mutableMapOf()

    /**
     * Encoding element's information can be obtained from the [descriptor] by the given [index].
     */
    private var index: Int = 0

    /**
     * Throw IllegalArgumentException if object depth, defined as objects within objects, larger
     * than 500.
     */
    init {
        if (depth == MAX_DEPTH) {
            throw IllegalArgumentException(
                "Exceeded maximum depth of $MAX_DEPTH, which likely indicates there's an object cycle"
            )
        }
    }

    /** Returns the final encoded result. */
    fun serializedResult() = encodedMap.toMap() // a defensive deep copy

    /** The data class records the information for the element that needs to be encoded. */
    private inner class Element(elementIndex: Int = 0) {
        val encodeKey: String = descriptor.getElementName(elementIndex)
        val elementAnnotations: List<Annotation> = descriptor.getElementAnnotations(elementIndex)
        val elementSerialName = descriptor.getElementDescriptor(elementIndex).serialName
        val elementSerialKind = descriptor.getElementDescriptor(elementIndex).kind
    }

    /** Get the field name of an enum via index, and encode it. */
    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) =
        encodeValue(enumDescriptor.getElementName(index))

    override val serializersModule: SerializersModule =
        FirestoreSerializersModule.getFirestoreSerializersModule()

    /**
     * Encode the native Firestore datatype objects: [DocumentId], [Timestamp], [Date], and
     * [GeoPoint].
     */
    override fun encodeFirestoreNativeDataType(value: Any): Unit =
        encodedMap.let {
            val element: Element = Element(index++)
            validateAnnotations(element)
            when {
                // @DocumentId on DocumentReference, then ignore
                validateDocumentIdPresentOrThrow(element) -> {}
                // @ServerTimestamp on Date, Timestamp, then encode as it is.
                else -> it[element.encodeKey] = value
            }
        }

    override fun encodeNull(): Unit =
        encodedMap.let {
            val element: Element = Element(index++)
            validateAnnotations(element)
            when {
                // @DocumentId on String?, DocumentReference?, then ignore.
                validateDocumentIdPresentOrThrow(element) -> {}
                // @ServerTimestamp on Date?, Timestamp?, then encode as FieldValue.
                validateServerTimestampPresentOrThrow(element) ->
                    it[element.encodeKey] = FieldValue.serverTimestamp()
                else -> it[element.encodeKey] = null
            }
        }

    override fun encodeValue(value: Any): Unit =
        encodedMap.let {
            val element: Element = Element(index++)
            validateAnnotations(element)
            when {
                // @DocumentId on String, then ignore, @ServerTimestamp cannot on Primitive types
                validateDocumentIdPresentOrThrow(element) -> {}
                else -> it[element.encodeKey] = value
            }
        }

    override fun endStructure(descriptor: SerialDescriptor) = callback(encodedMap)

    /**
     * Recursively build the nested map when an encoded property has its own structure (i.e. a
     * nested @[Serializable] object, or a nested list).
     *
     * @param descriptor the [SerialDescriptor] of the @[Serializable] object.
     * @return a CompositeEncoder either to be a [FirestoreMapEncoder] or a [FirestoreListEncoder].
     */
    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        if (depth == 0) {
            return FirestoreMapEncoder(descriptor, depth = depth + 1) { encodedMap.putAll(it) }
        }
        val currentElement = Element(index++)
        validateAnnotations(currentElement)
        return when (descriptor.kind) {
            StructureKind.CLASS -> {
                FirestoreMapEncoder(descriptor, depth = depth + 1) {
                    encodedMap[currentElement.encodeKey] = it
                }
            }
            StructureKind.LIST -> {
                FirestoreListEncoder(depth = depth + 1) {
                    encodedMap[currentElement.encodeKey] = it
                }
            }
            else -> {
                throw IllegalArgumentException(
                    "Incorrect format of nested object provided: <$descriptor.kind>"
                )
            }
        }
    }

    /**
     * Returns true if @[DocumentId] is present and applied on a property of String or
     * DocumentReference; Returns false if @[DocumentId] is absent; Throws runtime exception if @
     * [DocumentId] is present but applied on a property of an invalid type.
     */
    private fun validateDocumentIdPresentOrThrow(currentElement: Element): Boolean {
        val documentIdPresent = currentElement.elementAnnotations?.any { it is DocumentId }
        return if (documentIdPresent) {
            documentIdAppliedOnValidProperty(currentElement)
        } else {
            false
        }
    }

    /**
     * Returns true if the @[DocumentId] is applied on a property of a type [String] or
     * [DocumentReference]; Otherwise, a runtime exception will be thrown.
     */
    private fun documentIdAppliedOnValidProperty(currentElement: Element): Boolean {
        val regex = Regex("<DocumentReference>|__DocumentReferenceSerializer__|<String>")
        val isDocumentReference = currentElement.elementSerialName?.contains(regex)
        if (currentElement.elementSerialKind == PrimitiveKind.STRING || isDocumentReference) {
            return true
        } else {
            throw IllegalArgumentException(
                "Field is annotated with @DocumentId but is class $currentElement.elementKind ( with serial name ${currentElement.elementSerialName} ) instead of String or DocumentReference."
            )
        }
    }

    /**
     * Returns true if @[ServerTimestamp] is present and applied on a property of Date or Timestamp;
     * Returns false if @[ServerTimestamp] is absent; Throws runtime exception if @
     * [ServerTimestamp] is present but applied on a property of an invalid type.
     */
    private fun validateServerTimestampPresentOrThrow(currentElement: Element): Boolean {
        val serverTimestampPresent =
            currentElement.elementAnnotations.any { it is ServerTimestamp }
        return if (serverTimestampPresent) {
            serverTimestampAppliedOnValidProperty(currentElement)
        } else {
            false
        }
    }

    /**
     * Returns true if the @[ServerTimestamp] is applied on a property of a type Date or Timestamp;
     * Otherwise, a runtime exception will be thrown.
     */
    private fun serverTimestampAppliedOnValidProperty(currentElement: Element): Boolean {
        val regex = Regex("<Timestamp>|__TimestampSerializer__")
        val isOnTimestamp = currentElement.elementSerialName.contains(regex)
        val isOnDate = currentElement.elementSerialName.contains("<Date>")
        if (isOnTimestamp || isOnDate) {
            return true
        } else {
            throw IllegalArgumentException(
                "Field is annotated with @ServerTimestamp but is class $currentElement.elementKind ( with serial name ${currentElement.elementSerialName} ) instead of Date or Timestamp."
            )
        }
    }

    private fun validateAnnotations(currentElement: Element) {
        validateDocumentIdPresentOrThrow(currentElement)
        validateServerTimestampPresentOrThrow(currentElement)
    }
}

/**
 * Encodes a list of the @[Serializable] objects into a list of Firestore supported types.
 *
 * @param depth Object depth, defined as objects within objects.
 * @param callback PSends the encoded nested map back to the caller.
 */
private class FirestoreListEncoder(
    private val depth: Int = 0,
    private val callback: (MutableList<Any?>) -> Unit
) : FirestoreAbstractEncoder, AbstractEncoder() {

    /** A list that saves the encoding result. */
    private val encodedList: MutableList<Any?> = mutableListOf()

    /**
     * Throw IllegalArgumentException if object depth, defined as objects within objects, larger
     * than 500.
     */
    init {
        if (depth == MAX_DEPTH) {
            throw IllegalArgumentException(
                "Exceeded maximum depth of $MAX_DEPTH, which likely indicates there's an object cycle"
            )
        }
    }

    override val serializersModule: SerializersModule =
        FirestoreSerializersModule.getFirestoreSerializersModule()

    override fun encodeValue(value: Any): Unit = encodedList.let { it.add(value) }

    override fun encodeNull(): Unit = encodedList.let { it.add(null) }

    override fun encodeFirestoreNativeDataType(value: Any): Unit = encodedList.let { it.add(value) }

    override fun endStructure(descriptor: SerialDescriptor) = callback(encodedList)

    /**
     * Recursively encoding if the elements inside of the list are @[Serializable] objects.
     *
     * @param descriptor the [SerialDescriptor] of the @[Serializable] object.
     * @return a [FirestoreMapEncoder] represent the @[Serializable] list element.
     */
    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        when (descriptor.kind) {
            StructureKind.CLASS -> {
                return FirestoreMapEncoder(descriptor, depth = depth + 1) { encodedList.add(it) }
            }
            else -> {
                throw IllegalArgumentException(
                    "Incorrect format of nested object provided: <$descriptor.kind>"
                )
            }
        }
    }
}

private const val MAX_DEPTH: Int = 500

/**
 * Encodes a @[Serializable] object to a nested map of Firestore supported types.
 *
 * @param serializer The [SerializationStrategy] of the @[Serializable] object.
 * @param value The @[Serializable] object.
 * @return The encoded nested map of Firestore supported types.
 */
fun <T> encodeToMap(serializer: SerializationStrategy<T>, value: T): Map<String, Any?> {
    val encoder = FirestoreMapEncoder(serializer.descriptor) {}
    encoder.encodeSerializableValue(serializer, value)
    return encoder.serializedResult()
}

inline fun <reified T> encodeToMap(value: T): Map<String, Any?> = encodeToMap(serializer(), value)
