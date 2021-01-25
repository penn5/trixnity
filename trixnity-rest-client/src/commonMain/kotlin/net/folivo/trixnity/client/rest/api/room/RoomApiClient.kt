package net.folivo.trixnity.client.rest.api.room

import com.benasher44.uuid.uuid4
import io.ktor.client.*
import io.ktor.client.request.*
import net.folivo.matrix.restclient.api.rooms.Membership
import net.folivo.trixnity.client.rest.api.room.CreateRoomRequest.Invite3Pid
import net.folivo.trixnity.client.rest.api.room.CreateRoomRequest.Preset
import net.folivo.trixnity.client.rest.api.room.Direction.FORWARD
import net.folivo.trixnity.client.rest.api.room.JoinRoomRequest.ThirdPartySigned
import net.folivo.trixnity.client.rest.e
import net.folivo.trixnity.core.model.MatrixId.*
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.RoomEvent
import net.folivo.trixnity.core.model.events.StateEvent
import net.folivo.trixnity.core.model.events.m.room.CreateEvent
import net.folivo.trixnity.core.model.events.m.room.MemberEvent
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEvent
import net.folivo.trixnity.core.model.events.m.room.RedactionEvent.RedactionEventContent
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMapping

class RoomApiClient(
    val httpClient: HttpClient,
    roomEventSerializers: Set<EventContentSerializerMapping<out RoomEvent<*>, *>>,
    stateEventSerializers: Set<EventContentSerializerMapping<out StateEvent<*>, *>>
) {

    val roomEventLookup = roomEventSerializers.map { Pair(it.eventClass, it.type) }.toMap()
    val stateEventLookup = stateEventSerializers.map { Pair(it.eventClass, it.type) }.toMap()


    companion object {
        const val unsupportedEventType =
            "Event type is not supported. If it is a custom type, you should register it in MatrixClient. " +
                    "If not, ensure, that you use the generic fields (e. g. sendStateEvent<NameEvent, NameEventContent>(...)) " +
                    "so that we can determine the right event type."
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-client-r0-rooms-roomid-event-eventid">matrix spec</a>
     */
    suspend fun getEvent(
        roomId: RoomId,
        eventId: EventId,
        asUserId: UserId? = null
    ): Event<*> {
        return httpClient.get {
            url("/r0/rooms/${roomId.e()}/event/${eventId.e()}")
            parameter("user_id", asUserId)
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-client-r0-rooms-roomid-state-eventtype-statekey">matrix spec</a>
     */
    suspend inline fun <reified T : StateEvent<C>, reified C> getStateEvent(
        roomId: RoomId,
        stateKey: String = "",
        asUserId: UserId? = null
    ): C {
        val eventType = stateEventLookup[T::class] ?: throw IllegalArgumentException(unsupportedEventType)
        return httpClient.get {
            url("/r0/rooms/${roomId.e()}/state/$eventType/$stateKey")
            parameter("user_id", asUserId)
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-client-r0-rooms-roomid-state">matrix spec</a>
     */
    suspend fun getState(roomId: RoomId, asUserId: UserId? = null): List<StateEvent<*>> {
        return httpClient.get {
            url("/r0/rooms/${roomId.e()}/state")
            parameter("user_id", asUserId)
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-client-r0-rooms-roomid-members">matrix spec</a>
     */
    suspend fun getMembers(
        roomId: RoomId,
        at: String? = null,
        membership: Membership? = null,
        notMembership: Membership? = null,
        asUserId: UserId? = null
    ): List<MemberEvent> {
        return httpClient.get<GetMembersResponse> {
            url("/r0/rooms/${roomId.e()}/members")
            parameter("at", at)
            parameter("membership", membership?.value)
            parameter("not_membership", notMembership?.value)
            parameter("user_id", asUserId)
        }.chunk
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-client-r0-rooms-roomid-joined-members">matrix spec</a>
     */
    suspend fun getJoinedMembers(
        roomId: RoomId,
        asUserId: UserId? = null
    ): GetJoinedMembersResponse {
        return httpClient.get {
            url("/r0/rooms/${roomId.e()}/joined_members")
            parameter("user_id", asUserId)
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-client-r0-rooms-roomid-messages">matrix spec</a>
     */
    suspend fun getEvents(
        roomId: RoomId,
        from: String,
        dir: Direction = FORWARD,
        to: String? = null,
        limit: Long = 10,
        filter: String? = null,
        asUserId: UserId? = null
    ): GetEventsResponse {
        return httpClient.get {
            url("/r0/rooms/${roomId.e()}/messages")
            parameter("from", from)
            parameter("to", to)
            parameter("dir", dir.value)
            parameter("limit", limit.toString())
            parameter("filter", filter)
            parameter("user_id", asUserId)
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#put-matrix-client-r0-rooms-roomid-state-eventtype-statekey">matrix spec</a>
     */
    suspend inline fun <reified T : StateEvent<in C>, C : Any> sendStateEvent(
        roomId: RoomId,
        eventContent: C,
        stateKey: String? = "",
        asUserId: UserId? = null
    ): EventId {
        val eventType = stateEventLookup[T::class] ?: throw IllegalArgumentException(unsupportedEventType)
        return httpClient.put<SendEventResponse> {
            url("/r0/rooms/${roomId.e()}/state/$eventType/$stateKey")
            parameter("user_id", asUserId)
            body = eventContent
        }.eventId
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#put-matrix-client-r0-rooms-roomid-send-eventtype-txnid">matrix spec</a>
     */
    suspend inline fun <reified T : RoomEvent<in C>, C : Any> sendRoomEvent(
        roomId: RoomId,
        eventContent: C,
        txnId: String = uuid4().toString(),
        asUserId: UserId? = null
    ): EventId {
        val eventType = roomEventLookup[T::class] ?: throw IllegalArgumentException(unsupportedEventType)
        return httpClient.put<SendEventResponse> {
            url("/r0/rooms/${roomId.e()}/send/$eventType/$txnId")
            parameter("user_id", asUserId)
            body = eventContent
        }.eventId
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#put-matrix-client-r0-rooms-roomid-redact-eventid-txnid">matrix spec</a>
     */
    suspend fun sendRedactEvent(
        roomId: RoomId,
        eventId: EventId,
        reason: String,
        txnId: String = uuid4().toString(),
        asUserId: UserId? = null
    ): EventId {
        return httpClient.put<SendEventResponse> {
            url("/r0/rooms/${roomId.e()}/redact/${eventId.e()}/$txnId")
            parameter("user_id", asUserId)
            body = RedactionEventContent(reason)
        }.eventId
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-createroom">matrix spec</a>
     */
    suspend fun createRoom(
        visibility: Visibility = Visibility.PRIVATE,
        roomAliasId: RoomAliasId? = null,
        name: String? = null,
        topic: String? = null,
        invite: Set<UserId>? = null,
        invite3Pid: Set<Invite3Pid>? = null,
        roomVersion: String? = null,
        creationContent: CreateEvent.CreateEventContent? = null,
        initialState: List<StateEvent<Any>>? = null,
        preset: Preset? = null,
        isDirect: Boolean? = null,
        powerLevelContentOverride: PowerLevelsEvent.PowerLevelsEventContent? = null,
        asUserId: UserId? = null
    ): RoomId {
        return httpClient.post<CreateRoomResponse> {
            url("/r0/createRoom")
            parameter("user_id", asUserId)
            body = CreateRoomRequest(
                visibility,
                roomAliasId?.localpart,
                name,
                topic,
                invite,
                invite3Pid,
                roomVersion,
                creationContent,
                initialState,
                preset,
                isDirect,
                powerLevelContentOverride
            )
        }.roomId
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#put-matrix-client-r0-directory-room-roomalias">matrix spec</a>
     */
    suspend fun setRoomAlias(
        roomId: RoomId,
        roomAliasId: RoomAliasId,
        asUserId: UserId? = null
    ) {
        return httpClient.put {
            url("/r0/directory/room/${roomAliasId.e()}")
            parameter("user_id", asUserId)
            body = SetRoomAliasRequest(roomId)
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-client-r0-directory-room-roomalias">matrix spec</a>
     */
    suspend fun getRoomAlias(
        roomAliasId: RoomAliasId,
        asUserId: UserId? = null
    ): GetRoomAliasResponse {
        return httpClient.get {
            url("/r0/directory/room/${roomAliasId.e()}")
            parameter("user_id", asUserId)
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#delete-matrix-client-r0-directory-room-roomalias">matrix spec</a>
     */
    suspend fun deleteRoomAlias(
        roomAliasId: RoomAliasId,
        asUserId: UserId? = null
    ) {
        return httpClient.delete {
            url("/r0/directory/room/${roomAliasId.e()}")
            parameter("user_id", asUserId)
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-client-r0-joined-rooms">matrix spec</a>
     */
    suspend fun getJoinedRooms(asUserId: UserId? = null): Set<RoomId> {
        return httpClient.get<GetJoinedRoomsResponse> {
            url("/r0/joined_rooms")
            parameter("user_id", asUserId)
        }.joinedRooms
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-rooms-roomid-invite">matrix spec</a>
     */
    suspend fun inviteUser(
        roomId: RoomId,
        userId: UserId,
        asUserId: UserId? = null
    ) {
        return httpClient.post {
            url("/r0/rooms/${roomId.e()}/invite")
            parameter("user_id", asUserId)
            body = InviteUserRequest(userId)
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-join-roomidoralias">matrix spec</a>
     */
    suspend fun joinRoom(
        roomId: RoomId,
        serverNames: Set<String>? = null,
        thirdPartySigned: ThirdPartySigned? = null,
        asUserId: UserId? = null
    ): RoomId {
        return httpClient.post<JoinRoomResponse> {
            url("/r0/join/${roomId.e()}")
            serverNames?.forEach { parameter("server_name", it) }
            parameter("user_id", asUserId)
            body = JoinRoomRequest(thirdPartySigned)
        }.roomId
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-join-roomidoralias">matrix spec</a>
     */
    suspend fun joinRoom(
        roomAliasId: RoomAliasId,
        serverNames: Set<String>? = null,
        thirdPartySigned: ThirdPartySigned? = null,
        asUserId: UserId? = null
    ): RoomId {
        return httpClient.post<JoinRoomResponse> {
            url("/r0/join/${roomAliasId.e()}")
            serverNames?.forEach { parameter("server_name", it) }
            parameter("user_id", asUserId)
            body = JoinRoomRequest(thirdPartySigned)
        }.roomId
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-rooms-roomid-leave">matrix spec</a>
     */
    suspend fun leaveRoom(
        roomId: RoomId,
        asUserId: UserId? = null
    ) {
        return httpClient.post {
            url("/r0/rooms/${roomId.e()}/leave")
            parameter("user_id", asUserId)
        }
    }

//    /**
//     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-rooms-roomid-forget">matrix spec</a>
//     */
//    fun forgetRoom() {
//        // TODO implement
//    }

//    /**
//     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-rooms-roomid-kick">matrix spec</a>
//     */
//    fun kickUser() {
//        // TODO implement
//    }

//    /**
//     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-rooms-roomid-ban">matrix spec</a>
//     */
//    fun banUser() {
//        // TODO implement
//    }

//    /**
//     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-rooms-roomid-unban">matrix spec</a>
//     */
//    fun unbanUser() {
//        // TODO implement
//    }

//    /**
//     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-client-r0-directory-list-room-roomid">matrix spec</a>
//     */
//    fun getVisibility() {
//        // TODO implement
//    }

//    /**
//     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#put-matrix-client-r0-directory-list-room-roomid">matrix spec</a>
//     */
//    fun setVisibility() {
//        // TODO implement
//    }

//    /**
//     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-publicrooms">matrix spec</a>
//     */
//    fun getPublicRooms() {
//        // TODO implement
//    }
}