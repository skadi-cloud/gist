import {Controller} from "@hotwired/stimulus";
import EasyMDE from "easymde";

export default class extends Controller {
    private mde: EasyMDE;
    static targets = ["edit"]
    declare readonly editTarget: HTMLTextAreaElement
    connect() {
        let linkElement = document.createElement("link");
        linkElement.rel = "stylesheet"
        linkElement.href = "https://unpkg.com/easymde@2.15.0/dist/easymde.min.css"
        this.editTarget.parentElement?.appendChild(linkElement)
        this.mde = new EasyMDE({element: this.editTarget, status: false, placeholder: "No description."})
    }
}