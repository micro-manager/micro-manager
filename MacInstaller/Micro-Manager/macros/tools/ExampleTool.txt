// This macro set demonstrates the three
// ways a user can run a tool macro:
//
//    1. Using the tool to click on an image
//    2. Single-clicking on the tool icon
//    3. Double-clicking on the tool icon

    macro "Example Tool - C059o11ee" {
        getCursorLoc(x, y, z, flags);
        print("User clicked on the image at "+x+","+y);
    }

    macro "Example Tool Selected" {
        requires("1.33h");
        print("User clicked on the tool icon");
    }

    macro "Example Tool Options" {
        print("User double-clicked on the tool icon");
    }
