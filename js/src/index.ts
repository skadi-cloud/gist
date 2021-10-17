import * as Turbo from "@hotwired/turbo"

import {Application} from '@hotwired/stimulus';
import {definitionsFromContext} from '@hotwired/stimulus-webpack-helpers';

const application = Application.start();
const context = require.context('./controllers', true, /\.ts$/);
application.load(definitionsFromContext(context));
Turbo.start()