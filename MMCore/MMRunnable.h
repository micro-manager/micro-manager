


class MMRunnable
{
public:
   enum RunnableType {OTHER, TIME, POSITION, CHANNEL, SLICE, IMAGE, AUTOFOCUS};

   RunnableType type;

   virtual ~MMRunnable() {};
   virtual void run() = 0;

};
