package pro.gravit.launchserver.socket.response.auth;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import pro.gravit.launcher.ClientPermissions;
import pro.gravit.launcher.events.RequestEvent;
import pro.gravit.launcher.events.request.ExitRequestEvent;
import pro.gravit.launchserver.LaunchServer;
import pro.gravit.launchserver.socket.Client;
import pro.gravit.launchserver.socket.handlers.WebSocketFrameHandler;
import pro.gravit.launchserver.socket.response.SimpleResponse;

public class ExitResponse extends SimpleResponse {
    public boolean exitAll;
    public String username;

    @Override
    public String getType() {
        return "exit";
    }

    @Override
    public void execute(ChannelHandlerContext ctx, Client client) throws Exception {
        if (username != null && (!client.isAuth || client.permissions == null || !client.permissions.isPermission(ClientPermissions.PermissionConsts.ADMIN))) {
            sendError("Permissions denied");
            return;
        }
        if (username == null) {
            if (client.session == null && exitAll) {
                sendError("Session invalid");
                return;
            }
            WebSocketFrameHandler handler = ctx.pipeline().get(WebSocketFrameHandler.class);
            if (handler == null) {
                sendError("Exit internal error");
                return;
            }
            Client newClient = new Client(null);
            newClient.checkSign = client.checkSign;
            handler.setClient(newClient);
            if (client.session != null) server.sessionManager.removeClient(client.session);
            if (exitAll) {
                service.forEachActiveChannels(((channel, webSocketFrameHandler) -> {
                    Client client1 = webSocketFrameHandler.getClient();
                    if (client.isAuth && client.username != null) {
                        if (!client1.isAuth || !client.username.equals(client1.username)) return;
                    } else {
                        if (client1.session != client.session) return;
                    }
                    exit(server, webSocketFrameHandler, channel, ExitRequestEvent.ExitReason.SERVER);
                }));
            }
            sendResult(new ExitRequestEvent(ExitRequestEvent.ExitReason.CLIENT));
        } else {
            service.forEachActiveChannels(((channel, webSocketFrameHandler) -> {
                Client client1 = webSocketFrameHandler.getClient();
                if (client1 != null && client.isAuth && client.username != null && client1.username.equals(username)) {
                    exit(server, webSocketFrameHandler, channel, ExitRequestEvent.ExitReason.SERVER);
                }
            }));
            sendResult(new ExitRequestEvent(ExitRequestEvent.ExitReason.NO_EXIT));
        }
    }

    public static void exit(LaunchServer server, WebSocketFrameHandler wsHandler, Channel channel, ExitRequestEvent.ExitReason reason) {

        Client chClient = wsHandler.getClient();
        Client newCusClient = new Client(null);
        newCusClient.checkSign = chClient.checkSign;
        wsHandler.setClient(newCusClient);
        if (chClient.session != null) server.sessionManager.removeClient(chClient.session);
        ExitRequestEvent event = new ExitRequestEvent(reason);
        event.requestUUID = RequestEvent.eventUUID;
        wsHandler.service.sendObject(channel, event);
    }
}
