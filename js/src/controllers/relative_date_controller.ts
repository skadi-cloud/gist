import {Controller} from "@hotwired/stimulus"
import {FormatString} from "../String";

export default class extends Controller {
    declare readonly itemTarget: HTMLElement;
    static targets = ["item"]
    private timeout: NodeJS.Timeout;

    declare readonly dateValue: string;
    declare readonly formatValue?: string;
    static values = {
        date: String,
        format: String
    };

    connect() {
        let element = this.itemTarget
        let dateAttr = this.dateValue
        let template = this.formatValue
        let updater = function () {
                let diff = new Date().getTime() - new Date(Number.parseInt(dateAttr) + new Date().getTimezoneOffset() * 60000).getTime()
                let diffStr
                if (diff > 86400000) {
                    diffStr = Math.floor(diff / 86400000) + " days"
                } else if (diff > 3600000) {
                    diffStr = Math.floor(diff / 3600000) + " hours"
                } else if (diff > 60000) {
                    diffStr = Math.floor(diff / 60000) + " minutes"
                } else {
                    diffStr = Math.floor(diff / 1000) + " seconds"
                }
                if(template != null && template.length > 0) {
                    element.innerText = FormatString(template, diffStr + " ago")
                } else {
                    element.innerText = diffStr + " ago"
                }
            }
        this.timeout = setInterval(updater, 10000);
        updater()
    }

    disconnect() {
        clearTimeout(this.timeout)
    }
}