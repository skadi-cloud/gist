import {Controller} from "@hotwired/stimulus";

export default class extends Controller {
    private startPort = 63342
    private portCount = 20
    private mpsPort? : Number

    declare readonly failedTarget: HTMLDivElement;
    declare readonly detectingTarget: HTMLDivElement;
    declare readonly readyTarget: HTMLDivElement;

    static targets = ["failed", "detecting", "ready"]

    declare readonly gistIdValue: string;
    static values = {
        gistId: String
    };

    connect() {
        this.findPort()
    }

    private findPort(): Number {
        this.detectingTarget.classList.remove("hidden")
        this.readyTarget.classList.add("hidden")
        this.failedTarget.classList.add("hidden")
        let controller = this
        let promisses: Promise<any>[] = []
        for (let port = this.startPort; port <= this.startPort + this.portCount; port++) {
            let promise =  fetch(`http://localhost:${port}/skadi-gist/hello`, {
                method: "GET",
                mode: "cors",
                credentials: "omit",
                cache: "no-cache"
            }).then(function (response) {
                if(response.ok) {
                    controller.mpsPort = port
                    controller.readyTarget.classList.remove("hidden")
                    controller.detectingTarget.classList.add("hidden")
                }
            }).catch(function (reason) {
                console.log(reason)
            })
            promisses.push(promise)
        }
        Promise.all(promisses).then(function (x) {
            if(controller.mpsPort == null) {
                controller.detectingTarget.classList.add("hidden")
                controller.failedTarget.classList.remove("hidden")
            }
        })
        return 0
    }

    retry(event:MouseEvent) {
        this.findPort()
        event.preventDefault()
        event.stopPropagation()
    }

    doImport(event: MouseEvent) {
        if(this.mpsPort != null) {
            event.preventDefault()
            event.stopPropagation()
            let port = this.mpsPort
            let url = new URL(`http://localhost:${port}/skadi-gist/import-gist`)
            url.searchParams.append("gist", this.gistIdValue)
            fetch(url.toString(), {
                method: "POST",
                mode: "cors",
                credentials: "omit",
                cache: "no-cache"
            }).then(function (response) {
                if(response.ok) {
                    console.log("ok")
                    response.text().then(function (text) {
                        console.log(text)
                    })
                } else {
                    console.log("error")
                }
            })
        }
    }
}