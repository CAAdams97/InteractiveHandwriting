package group6.interactivehandwriting.common.network;

import java.util.List;
import java.util.Set;

import group6.interactivehandwriting.common.app.actions.DrawActionHandle;
import group6.interactivehandwriting.common.app.actions.draw.EndDrawAction;
import group6.interactivehandwriting.common.app.actions.draw.MoveDrawAction;
import group6.interactivehandwriting.common.app.actions.draw.StartDrawAction;
import group6.interactivehandwriting.common.app.Profile;
import group6.interactivehandwriting.common.app.rooms.Room;

/**
 * Created by JakeL on 9/22/18.
 */

public interface NetworkLayer<T> {
    public void begin(final Profile profile);
    public Profile getMyProfile();

    public Set<Room> getRooms();
    public void joinRoom(final Profile profile, final Room room);
    public void exitRoom();

    public void receiveDrawActions(final DrawActionHandle handle);
    public void startDraw(final StartDrawAction action);
    public void moveDraw(final MoveDrawAction action);
    public void endDraw(final EndDrawAction action);
}