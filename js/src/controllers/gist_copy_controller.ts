import {Controller} from "@hotwired/stimulus";

export default class extends Controller {

    static targets = ["button", "text"]
    declare readonly buttonTarget: HTMLButtonElement
    declare readonly textTarget: HTMLElement

    static classes = ["copied"]
    declare readonly copiedClasses: string

    connect() {
    }


    doCopy(event: MouseEvent) {
        event.preventDefault()
        let type = "text/plain"
        let blob = new Blob([document.location.toString()], {type})
        // @ts-ignore
        let data = [new ClipboardItem({[type]: blob})]

        let buttonTarget = this.buttonTarget
        let textTarget = this.textTarget
        let copiedClasses = this.copiedClasses
        navigator.clipboard.write(data).then(
            function () {
                let originalText = textTarget.innerText
                textTarget.innerText = "Copied"
                buttonTarget.classList.add(copiedClasses)
                setTimeout(() => {
                    buttonTarget.classList.remove(copiedClasses)
                    textTarget.innerText = originalText
                }, 1000)
            },
            function () {
                console.error("failed to copy")
                let originalText = buttonTarget.innerText
                textTarget.innerText = "Error"
                buttonTarget.classList.add(copiedClasses)
                setTimeout(() => {
                    buttonTarget.classList.remove(copiedClasses)
                    textTarget.innerText = originalText
                }, 1000)
            }
        )

    }
}

