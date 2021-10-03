import * as Turbo from "@hotwired/turbo"

import {Application} from '@hotwired/stimulus';
import {definitionsFromContext} from '@hotwired/stimulus-webpack-helpers';

const application = Application.start();
const context = require.context('./controllers', true, /\.ts$/);
application.load(definitionsFromContext(context));
Turbo.start()

let loc = window.location, new_uri;
if (loc.protocol === "https:") {
    new_uri = "wss:";
} else {
    new_uri = "ws:";
}
new_uri += "//" + loc.host;
new_uri += loc.pathname + "" + loc.search;

const es = new WebSocket(new_uri);
Turbo.connectStreamSource(es)