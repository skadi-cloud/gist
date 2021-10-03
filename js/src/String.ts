export function FormatString(str: string, ...val: string[]) {
    return str.replace(/{(\d+)}/g, function(match, number) {
        return typeof val[number] != 'undefined'
            ? val[number]
            : match
            ;
    });
}