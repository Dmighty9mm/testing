package net.lax1dude.eaglercraft.v2_6.client.apollo.module;

import java.util.List;

import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoMessage;

/**
 * transfer: server-initiated pings of / transfers to other server
 * addresses. A browser client can only reach servers through a WebSocket
 * gateway, never a raw host:port, so pings answer TIMED_OUT and
 * transfers answer REJECTED - the server gets a correct, prompt reply
 * instead of waiting for a timeout.
 */
public final class TransferModule extends ApolloModule {

        static final int PING_TIMED_OUT = 2;
        static final int TRANSFER_REJECTED = 2;

        private int pingRequests;
        private int transferRequests;

        public TransferModule() {
                super("transfer", "Transfer");
        }

        @Override
        protected void register(Apollo.Router r) {
                r.on("transfer.v1.PingRequest", this::ping);
                r.on("transfer.v1.TransferRequest", this::transfer);
        }

        private void ping(ProtoMessage m) {
                pingRequests++;
                ProtoMessage resp = new ProtoMessage("lunarclient.apollo.transfer.v1.PingResponse");
                resp.set("requestId", m.getBytes("requestId"));
                List<String> ips = m.getStrings("serverIps");
                for (int i = 0; i < ips.size() && i < 64; i++) {
                        ProtoMessage d = new ProtoMessage("lunarclient.apollo.transfer.v1.PingData");
                        d.set("serverIp", ips.get(i));
                        d.set("status", PING_TIMED_OUT);
                        resp.add("pingData", d);
                }
                Apollo.send(resp);
        }

        private void transfer(ProtoMessage m) {
                transferRequests++;
                ProtoMessage resp = new ProtoMessage("lunarclient.apollo.transfer.v1.TransferResponse");
                resp.set("requestId", m.getBytes("requestId"));
                resp.set("status", TRANSFER_REJECTED);
                Apollo.send(resp);
        }

        @Override
        public void reset() {
                pingRequests = 0;
                transferRequests = 0;
        }

        @Override
        public String describeState() {
                return pingRequests + " ping / " + transferRequests + " transfer request(s) answered";
        }
}
