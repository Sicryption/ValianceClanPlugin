package com.encryptiron.boards;

class ModeTabClicks
{
    private int clicksSinceBoardOpened;
    private boolean inDoubtUntilClosed;

    void clicked()
    {
        clicksSinceBoardOpened++;
        if (clicksSinceBoardOpened > 1)
        {
            inDoubtUntilClosed = true;
        }
    }

    void boardOpened()
    {
        clicksSinceBoardOpened = 0;
    }

    void boardClosed()
    {
        clicksSinceBoardOpened = 0;
        inDoubtUntilClosed = false;
    }

    boolean isShownModeCertain()
    {
        return clicksSinceBoardOpened == 0 && !inDoubtUntilClosed;
    }
}
