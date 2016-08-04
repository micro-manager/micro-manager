
LRESULT CALLBACK
WindowProcedure(HWND hwnd, unsigned int message, WPARAM wParam, LPARAM lParam);


class WinClass
{
public:
   WinClass (WNDPROC winProc, char const * className, HINSTANCE hInst);
   void Register ()
   {
      ::RegisterClass (&_class);
   }

   void Unregister()
   {
      ::UnregisterClass(_class.lpszClassName, _class.hInstance);
   }

   void SetBackColor(COLORREF color)
   {
      _class.hbrBackground = CreateSolidBrush(color);
   }

private:
   WNDCLASS _class;
};


WinClass::WinClass(WNDPROC winProc, char const * className, HINSTANCE hInst)
{
   _class.style = 0;
   _class.lpfnWndProc = winProc; // window procedure: mandatory
   _class.cbClsExtra = 0;
   _class.cbWndExtra = 0;
   _class.hInstance = hInst;         // owner of the class: mandatory
   _class.hIcon = 0;
   _class.hCursor = ::LoadCursor (0, IDC_ARROW); // optional
   _class.hbrBackground = (HBRUSH) (COLOR_WINDOW + 1); // optional
   _class.lpszMenuName = 0;
   _class.lpszClassName = className; // mandatory
}


class WinMaker
{
public:
   WinMaker (): _hwnd (0) {}
   WinMaker (char const * caption,
         char const * className,
         HINSTANCE hInstance, int x, int y, int width, int height);
   void Show (int cmdShow)
   {
      ::ShowWindow (_hwnd, cmdShow);
      ::UpdateWindow (_hwnd);
   }

   HWND getHandle() { return _hwnd; }

protected:
   HWND _hwnd;
};


WinMaker::WinMaker(char const * caption,
      char const * className,
      HINSTANCE hInstance,
      int x,
      int y,
      int width,
      int height)
{
   _hwnd = ::CreateWindowEx (
         WS_EX_NOPARENTNOTIFY,
         className,            // name of a registered window class
         caption,              // window caption
         WS_POPUP | WS_CLIPCHILDREN | WS_CLIPSIBLINGS ,  // window style
         x,        // x position
         y,        // y position
         width,        // width
         height,        // height
         0,                    // handle to parent window
         0,                    // handle to menu
         hInstance,            // application instance
         0);                   // window creation data
}


// Window Procedure called by Windows
LRESULT CALLBACK
WindowProcedure(HWND hwnd, unsigned int message, WPARAM wParam, LPARAM lParam)
{
   switch (message)
   {
      case WM_DESTROY:
         ::PostQuitMessage (0);
         return 0;

   }
   return ::DefWindowProc (hwnd, message, wParam, lParam);
}
