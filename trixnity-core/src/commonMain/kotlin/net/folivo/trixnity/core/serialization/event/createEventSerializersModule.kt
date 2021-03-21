package net.folivo.trixnity.core.serialization.event

import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import net.folivo.trixnity.core.model.events.RoomEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import kotlin.reflect.KClass

fun createEventSerializersModule(
    roomEventContentSerializers: Set<EventContentSerializerMapping<out RoomEventContent>>,
    stateEventContentSerializers: Set<EventContentSerializerMapping<out StateEventContent>>
): SerializersModule {
    val basicEventSerializer = BasicEventSerializer()
    val roomEventSerializer = RoomEventSerializer(roomEventContentSerializers)
    val stateEventSerializer = StateEventSerializer(stateEventContentSerializers)
    val strippedStateEventSerializer = StrippedStateEventSerializer(stateEventContentSerializers)
    val eventSerializer = EventSerializer(
        basicEventSerializer,
        roomEventSerializer,
        stateEventSerializer,
        strippedStateEventSerializer
    )
    return SerializersModule {
        contextual(basicEventSerializer)
        contextual(eventSerializer)
        contextual(roomEventSerializer)
        contextual(stateEventSerializer)
        contextual(stateEventSerializer)

        roomEventContentSerializers.forEach {
            contextual(it.kClass as KClass<RoomEventContent>, it.serializer as KSerializer<RoomEventContent>)
        }
        stateEventContentSerializers.forEach {
            contextual(it.kClass as KClass<StateEventContent>, it.serializer as KSerializer<StateEventContent>)
        }
    }
}