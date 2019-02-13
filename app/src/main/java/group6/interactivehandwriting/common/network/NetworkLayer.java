package group6.interactivehandwriting.common.network;

import android.os.ParcelFileDescriptor;
import android.support.v7.app.AppCompatActivity;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

import group6.interactivehandwriting.activities.Room.RoomActivity;
import group6.interactivehandwriting.common.app.actions.DrawActionHandle;
import group6.interactivehandwriting.common.app.actions.draw.EndDrawAction;
import group6.interactivehandwriting.common.app.actions.draw.MoveDrawAction;
import group6.interactivehandwriting.common.app.actions.draw.StartDrawAction;
import group6.interactivehandwriting.common.app.Profile;
import group6.interactivehandwriting.common.app.rooms.Room;

/**
 * Created by JakeL on 9/22/18.
 */

public interface NetworkLayer {
    void begin(final Profile profile);
    Profile getMyProfile();

    void setRoomActivity(RoomActivity roomActivity);

    Set<Room> getRooms();
    void joinRoom(final Profile profile, final Room room);
    void synchronizeRoom();
    void exitRoom();

    void sendFile(ParcelFileDescriptor fd);
    void sendStream(InputStream inputStream);

    void receiveDrawActions(final DrawActionHandle handle);
    void startDraw(final StartDrawAction action);
    void moveDraw(final MoveDrawAction action);
    void endDraw(final EndDrawAction action);
    void undo(Profile profile);
}
