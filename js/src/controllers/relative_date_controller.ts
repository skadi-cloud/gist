import {Controller} from "@hotwired/stimulus"

export default class extends Controller {
    declare readonly dateTargets: HTMLElement[];
    static targets = ["date"]
    private timeout: NodeJS.Timeout;

    connect() {
        let elements = this.dateTargets

        let updater = function () {
            elements.forEach(function (element) {
                let dateAttr = element.attributes.getNamedItem("data-date")?.value
                let diff = new Date().getTime() - new Date(Number.parseInt(dateAttr!!) + new Date().getTimezoneOffset() * 60000).getTime()
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
                element.innerText = diffStr + " ago"
            })

        }
        this.timeout = setInterval(updater, 10000);
        updater()
    }

    disconnect() {
        clearTimeout(this.timeout)
    }
}