load("//tools:genrule2.bzl", "genrule2")

genrule2(
    name = "all",
    srcs = [
        "//owners",
        "//owners-autoassign",
    ],
    outs = ["all.zip"],
    cmd = " && ".join([
        "cp $(SRCS) $$TMP",
        "cd $$TMP",
        "zip -qr $$ROOT/$@ .",
    ]),
)
