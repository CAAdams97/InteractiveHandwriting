package group6.interactivehandwriting.common.network.nearby.connections;

import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.widget.Toast;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.connection.Payload;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import group6.interactivehandwriting.activities.Room.RoomActivity;
import group6.interactivehandwriting.activities.Video.ScreenShareService;
import group6.interactivehandwriting.activities.Video.VideoMenuActivity;
import group6.interactivehandwriting.activities.Video.VideoViewActivity;
import group6.interactivehandwriting.common.app.actions.Action;
import group6.interactivehandwriting.common.app.actions.DrawActionHandle;
import group6.interactivehandwriting.common.app.actions.draw.DrawableAction;
import group6.interactivehandwriting.common.app.actions.draw.EndDrawAction;
import group6.interactivehandwriting.common.app.actions.draw.MoveDrawAction;
import group6.interactivehandwriting.common.app.actions.draw.StartDrawAction;
import group6.interactivehandwriting.common.app.Profile;
import group6.interactivehandwriting.common.app.rooms.Room;
import group6.interactivehandwriting.common.network.NetworkLayerBinder;
import group6.interactivehandwriting.common.network.NetworkLayerService;
import group6.interactivehandwriting.common.network.nearby.connections.device.NCRoutingTable;
import group6.interactivehandwriting.common.network.nearby.connections.message.NetworkMessageType;
import group6.interactivehandwriting.common.network.nearby.connections.message.serial.NetworkSerializable;
import group6.interactivehandwriting.common.network.nearby.connections.message.serial.SerialMessage;
import group6.interactivehandwriting.common.network.nearby.connections.message.serial.SerialMessageHeader;
import group6.interactivehandwriting.common.network.nearby.connections.message.serial.data.SerialMessageData;
import group6.interactivehandwriting.common.network.nearby.connections.message.serial.data.draw.EndDrawActionMessage;
import group6.interactivehandwriting.common.network.nearby.connections.message.serial.data.draw.MoveDrawActionMessage;
import group6.interactivehandwriting.common.network.nearby.connections.message.serial.data.draw.StartDrawActionMessage;
import group6.interactivehandwriting.common.network.nearby.connections.message.serial.data.draw.UndoDrawMessage;
import group6.interactivehandwriting.common.network.nearby.connections.message.serial.data.room.RoomSynchronizeRequest;
import group6.interactivehandwriting.common.network.nearby.connections.message.serial.data.routing.RoutingUpdate;
import group6.interactivehandwriting.common.network.nearby.connections.message.serial.data.routing.RoutingUpdateRequest;

/**
 * Created by JakeL on 9/30/18.
 */

// TODO we should definitely create an object that encapsulates the HEADER, DATA section pair for byte[]
public class NCNetworkLayerService extends NetworkLayerService {

    private static boolean isActive = false;

    private NCRoutingTable routingTable;

    private NCNetworkConnection networkConnection;

    private Context context;
    private Profile myProfile;
    private Room myRoom;

    private DrawActionHandle drawActionHandle;

    private RoomActivity roomActivity;
    private VideoViewActivity videoViewActivity;
    private VideoMenuActivity videoMenuActivity;

    public boolean onConnectionInitiated(String endpointId) {
        Toast.makeText(context, "Device found with id " + endpointId, Toast.LENGTH_SHORT).show();

        return true; // TODO manage endpoint handshakes
    }

    public void onConnectionResult(String endpointId, Status connectionStatus) {
        Toast.makeText(context, "Device connected with endpoint id " + endpointId, Toast.LENGTH_SHORT).show();
        sendSerialMessage(new RoutingUpdateRequest(), endpointId);
    }

    public void onDisconnected(String endpointId) {
        Toast.makeText(context, "Device disconnected with id " + endpointId, Toast.LENGTH_SHORT).show();
    }

    public NCNetworkConnection getNCNetworkConnection() {
        return networkConnection;
    }

    @Override
    public int onStartCommand (Intent intent, int flags, int startId) {
        return START_STICKY_COMPATIBILITY;
    }

    @Override
    public NetworkLayerBinder onBind(Intent bindIntent) {
        return new NCNetworkLayerBinder(this);
    }

    @Override
    public void begin(final Profile profile) {
        if (!isActive) {
            isActive = true;
            this.myRoom = new Room();
            this.myProfile = profile;
            this.context = getApplicationContext();
            this.routingTable = new NCRoutingTable();
            this.routingTable.setMyProfile(profile);
            this.networkConnection = new NCNetworkConnection()
                    .forService(this)
                    .withProfile(profile);
            this.networkConnection.begin(context);

            Toast.makeText(context, "Starting Network Service", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void setRoomActivity(RoomActivity roomActivity) {
        this.roomActivity = roomActivity;
    }

    @Override
    public void setVideoViewActivity(VideoViewActivity videoViewActivity) {
        this.videoViewActivity = videoViewActivity;
    }

    @Override
    public void setVideoMenuActivity(VideoMenuActivity videoMenuActivity) {
        this.videoMenuActivity = videoMenuActivity;
    }

    @Override
    public Profile getMyProfile() {
        return myProfile;
    }

    @Override
    public Set<Room> getRooms() {
        sendSerialMessage(new RoutingUpdateRequest());
        Set<Room> rooms = routingTable.getRooms();
        return rooms;
    }

    @Override
    public void joinRoom(final Profile profile, final Room room) {
        myRoom = new Room(room.deviceId, room.name);
        routingTable.setMyRoom(profile, myRoom);
        sendRoutingUpdate();
    }

    @Override
    public void synchronizeRoom() {
        sendSerialMessage(new RoomSynchronizeRequest().withRoomNumber(myRoom.getRoomNumber()));
    }

    private void synchronizeRoomReply(String endpoint) {
        List<Action> actionHistory = drawActionHandle.getActionHistory();
        List<SerialMessageData> messages = new ArrayList<>();

        for (Action act : actionHistory) {
            if (act instanceof StartDrawAction) {
                messages.add(StartDrawActionMessage.fromAction((StartDrawAction) act));
            } else if (act instanceof MoveDrawAction) {
                messages.add(MoveDrawActionMessage.fromAction((MoveDrawAction) act));
            } else if (act instanceof EndDrawAction) {
                messages.add(EndDrawActionMessage.fromAction((EndDrawAction) act));
            }
        }

        for (SerialMessageData data : messages) {
            sendSerialMessage(data, endpoint);
        }
    }

    @Override
    public void exitRoom() {
        // cleanup
        routingTable.exitMyRoom(myProfile);
        myRoom = new Room();
        drawActionHandle = null;
        sendRoutingUpdate();
    }

    @Override
    public void sendBytes(byte[] bytes, NetworkMessageType messageType) {
        SerialMessageHeader header = new SerialMessageHeader()
                .withId(myProfile.deviceId)
                .withRoomNumber(myRoom.getRoomNumber())
                .withSequenceNumber(SerialMessageHeader.getNextSequenceNumber())
                .withType(messageType);
        if (messageType == NetworkMessageType.VIDEO_STREAM) {
            networkConnection.sendMessage(header, bytes, routingTable.getNeighborEndpoints());
        }
        else {
            SerialMessage message = new SerialMessage();
            header.withBigData((byte)0);
            message.withHeader(header).withData(bytes);
            networkConnection.sendBytes(header, bytes, routingTable.getNeighborEndpoints());
        }
    }

    @Override
    public void sendFile(ParcelFileDescriptor fd) {
        Payload filePayload = Payload.fromFile(fd);
        networkConnection.sendFile(filePayload, routingTable.getNeighborEndpoints());
    }

    @Override
    public void receiveDrawActions(DrawActionHandle handle) {
        this.drawActionHandle = handle;
    }

    @Override
    public void startDraw(StartDrawAction action) {
        sendSerialMessage(StartDrawActionMessage.fromAction(action));
    }

    @Override
    public void moveDraw(MoveDrawAction action) {
        sendSerialMessage(MoveDrawActionMessage.fromAction(action));
    }

    @Override
    public void endDraw(EndDrawAction action) {
        sendSerialMessage(EndDrawActionMessage.fromAction(action));
    }

    @Override
    public void undo(Profile p) {
        sendSerialMessage(new UndoDrawMessage().withProfile(p));
    }

    public void sendSerialMessage(NetworkSerializable data) {
        sendSerialMessage(data, routingTable.getNeighborEndpoints());
    }

    public void sendSerialMessage(NetworkSerializable data, final String endpoint) {
        List<String> endpoints = new ArrayList<>();
        endpoints.add(endpoint);
        sendSerialMessage(data, endpoints);
    }

    public void sendSerialMessage(NetworkSerializable data, List<String> endpoints) {
        SerialMessageHeader header = new SerialMessageHeader()
                .withId(myProfile.deviceId)
                .withRoomNumber(myRoom.getRoomNumber())
                .withSequenceNumber(SerialMessageHeader.getNextSequenceNumber())
                .withType(data.getType())
                .withBigData((byte)0);

        SerialMessage message = new SerialMessage();
        message.withHeader(header).withData(data);

        Payload payload = Payload.fromBytes(message.toBytes());
        networkConnection.sendMessage(payload, endpoints);
    }

    public void receiveMessage(String endpoint, Payload payload) {
        switch(payload.getType()) {
            case Payload.Type.FILE:
                handleFilePayload(endpoint, payload);
                break;
            case Payload.Type.BYTES:
                handleBytesPayload(endpoint, payload.asBytes());
                break;
            default:
                break;
        }
    }

    private void handleFilePayload(String endpoint, Payload payload) {
        if (payload != null) {
            File file = payload.asFile().asJavaFile();
            this.roomActivity.showPDF(file);
        }
    }

    private void handleBytesPayload(String endpoint, byte[] payloadBytes) {
        SerialMessage message = new SerialMessage().fromBytes(payloadBytes);
        SerialMessageHeader header = new SerialMessageHeader().fromBytes(message.getHeader());
        byte[] data = message.getData();

        if (myRoom != null &&
                myRoom.getRoomNumber() != Room.VOID_ROOM_NUMBER &&
                header.getRoomNumber() == myRoom.getRoomNumber()) {
            dispatchRoomMessage(endpoint, header, data);
        } else {
            dispatchMessage(endpoint, header, data);
        }
    }

    private void dispatchRoomMessage(String endpoint, SerialMessageHeader header, byte[] dataSection) {
        long id = header.getDeviceId();
        String username;
        switch(header.getType()) {
            case STREAM_STARTED:
                ScreenShareService.otherUserStreaming = true;
                if (videoMenuActivity != null) {
                    videoMenuActivity.setButtons();
                }
                username = routingTable.getProfile(header.getDeviceId()).username;
                Toast.makeText(context, "Stream started by " + username, Toast.LENGTH_LONG).show();
                break;
            case STREAM_ENDED:
                ScreenShareService.otherUserStreaming = false;
                if (videoMenuActivity != null) {
                    videoMenuActivity.setButtons();
                }
                username = routingTable.getProfile(header.getDeviceId()).username;
                Toast.makeText(context, "Stream ended by " + username, Toast.LENGTH_LONG).show();
                if (videoViewActivity != null) {
                    videoViewActivity.endViewing();
                }
                break;
            case VIDEO_STREAM:
                if (videoViewActivity != null) {
                    username = routingTable.getProfile(header.getDeviceId()).username;
                    videoViewActivity.showVideo(header, dataSection, username);
                }
            case RECEIVER_FPS:
                ScreenShareService.compareFPS((float) dataSection[0]);
                break;
            case START_DRAW:
                sendActionToCanvasManager(id, StartDrawActionMessage.actionFromBytes(dataSection));
                break;
            case MOVE_DRAW:
                sendActionToCanvasManager(id, MoveDrawActionMessage.actionFromBytes(dataSection));
                break;
            case END_DRAW:
                sendActionToCanvasManager(id, EndDrawActionMessage.actionFromBytes(dataSection));
                break;
            case UNDO_DRAW:
                if (drawActionHandle != null) {
                    drawActionHandle.undo(UndoDrawMessage.undoFromBytes(dataSection).getData());
                }
                break;
            case SYNC_REQUEST:
                synchronizeRoomReply(endpoint);
            default:
                break;
        }
    }

    private void dispatchMessage(String endpoint, SerialMessageHeader header, byte[] dataSection) {
        switch(header.getType()) {
            case ROUTING_UPDATE_REQUEST:
                sendRoutingUpdate(endpoint);
                break;
            case ROUTING_UPDATE_REPLY:
                handleRoutingUpdateReply(endpoint, new RoutingUpdate().fromBytes(dataSection));
                break;
            default:
                break;
        }
    }

    private void sendRoutingUpdate() {
        sendSerialMessage(new RoutingUpdate().withTable(routingTable));
    }

    private void sendRoutingUpdate(String endpoint) {
        sendSerialMessage(new RoutingUpdate().withTable(routingTable), endpoint);
    }


    private void handleRoutingUpdateReply(String endpoint, RoutingUpdate updateMessage) {
        routingTable.update(endpoint, updateMessage.getData());
    }

    private void sendActionToCanvasManager(long deviceId, DrawableAction action) {
        if (drawActionHandle != null) {
            drawActionHandle.handleDrawAction(routingTable.getProfile(deviceId), action);
        }
    }
}
