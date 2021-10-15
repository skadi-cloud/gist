
import { connectStreamSource, disconnectStreamSource } from "@hotwired/turbo";
import {Controller} from "@hotwired/stimulus";

export default class extends Controller {
    private es: WebSocket
    private declare readonly urlValue: string
    static values = { url: String };

    connect() {
        let loc = window.location, newProtocol;
        if (loc.protocol === "https:") {
            newProtocol = "wss:";
        } else {
            newProtocol = "ws:";
        }
        let wsUrl = new URL(this.urlValue);
        wsUrl.protocol = newProtocol

        this.es = new WebSocket(wsUrl);
        connectStreamSource(this.es);
    }

    disconnect() {
        this.es.close();
        disconnectStreamSource(this.es);
    }
}