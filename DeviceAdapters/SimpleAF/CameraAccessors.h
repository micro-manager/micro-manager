# ifndef _CAMERA_ACCESSORS_
# define _CAMERA_ACCESSORS_

# include <string>
# include "../../MMDevice/MMDevice.h"
# include "../../MMDevice/DeviceBase.h"
# include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/ModuleInterface.h"
# include <limits>
# include <iostream>
# include <iterator>

// Implementation of the camera pixel access and 
// camera pixel container

template <typename PixelType, class Allocator = std::allocator<PixelType> >
class CameraPixelContainer
{
public:
	//
	// Public typedefs for stl compliance
	// ----------------------------------
	//
	enum ConstructionType{INPLACE, OUTOFPLACE};
	typedef Allocator									allocator_type;
	typedef typename Allocator::value_type      		value_type;
	typedef typename Allocator::reference       		reference;
	typedef typename Allocator::const_reference 		const_reference;
	typedef typename Allocator::pointer         		pointer;
	typedef typename Allocator::const_pointer   		const_pointer;
	typedef typename Allocator::size_type       		size_type;
	typedef typename Allocator::difference_type 		difference_type;

private:
	// Pointer to the camera image data
	char *												cameradataraw_;
	PixelType *											ppixels_;
	PixelType											pixelvalue_;
	MM::Core *											core_;

public:
	// Base class for the iterator

template < bool bIsConst > class iterator_base
{
public:
    // Public typedefs

    typedef typename std::forward_iterator_tag			iterator_category;
    typedef typename Allocator::value_type				value_type;
    typedef typename Allocator::difference_type			difference_type;

    /// Select pointer or const_pointer to T.

    typedef typename tutils::select< bIsConst, const_pointer,
                        pointer >::result pointer;

    /// Select reference or const_reference to T.

    typedef typename tutils::select< bIsConst, const_reference,
                        reference >::result reference;

    /// Select node_pointer or node_const_pointer.

    typedef typename tutils::select< bIsConst, node_const_pointer,
                        node_pointer >::result node_pointer;
private:
    node_pointer get_node_pointer() { return m_pNode; }
    node_pointer m_pNode;
};

// Iterator typedefs


// Iterator. Used to iterate through a container. This is the normal mutable 
// iterator which allows modifying the pointed to object T.
typedef iterator_base< false > iterator;


// Const iterator. Used to iterate through a container. Not able to modify 
// the pointed to object T. 
typedef iterator_base< true > const_iterator;

/* Create with allocator. Construct empty container, allocator. */
explicit CameraPixelContainer(MM::Core * core)
: cameradataraw_(0), ppixels_(0), pixelvalue_(0) 
{
	core_ = core;
}

};

//template < typename T, class A = std::allocator< T > >
//class my_container 
//{
//public:
//// -----------------------
//
//// --- Typedefs PUBLIC ---
//
//// -----------------------
//
//typedef A                           allocator_type;
//typedef typename A::value_type      value_type;
//typedef typename A::reference       reference;
//typedef typename A::const_reference const_reference;
//typedef typename A::pointer         pointer;
//typedef typename A::const_pointer   const_pointer;
//typedef typename A::size_type       size_type;
//typedef typename A::difference_type difference_type;
//
//// ------------------------------------
//
//// --- NodeType Inner Class PRIVATE ---
//
//// ------------------------------------
//
//private:
//struct my_container_node
//{
//    // --- Typedefs ---
//
//    typedef my_container_node                            node_value_type;
//    typedef typename node_allocator::pointer             node_pointer;
//    typedef typename node_allocator::const_pointer       node_const_pointer;
//    typedef typename A::rebind< node_value_type >::other node_allocator;
//
//    // --- Member data ---
//
//    node_pointer m_pNext;
//    T            m_Data;
//};
//
//// --------------------------------------
//
//// --- Iterators Inner classes PUBLIC ---
//
//// --------------------------------------
//
//public:
//
///* Base iterator template class. Bsed on this two typedefs are made: one
// * for const_iterator and one for iterator. This way we make sure we only 
// * have to overload the iterator operators (like '++' '==' etc.) one 
// * place. */
//template < bool bIsConst > class iterator_base
//{
//public:
//    // --- Typedefs PUBLIC ---
//
//    typedef typename std::forward_iterator_tag iterator_category;
//    typedef typename A::value_type             value_type;
//    typedef typename A::difference_type        difference_type;
//
//    /// Select pointer or const_pointer to T.
//
//    typedef typename tutils::select< bIsConst, const_pointer,
//                        pointer >::result pointer;
//
//    /// Select reference or const_reference to T.
//
//    typedef typename tutils::select< bIsConst, const_reference,
//                        reference >::result reference;
//
//    /// Select node_pointer or node_const_pointer.
//
//    typedef typename tutils::select< bIsConst, node_const_pointer,
//                        node_pointer >::result node_pointer;
//private:
//    node_pointer get_node_pointer() { return m_pNode; }
//    node_pointer m_pNode;
//};
//
//
//// --- ITERATOR TYPEDEFS ---
//
//
///* Iterator. Used to iterate through a container. This is the normal mutable 
// * iterator which allows modifying the pointed to object T.*/
//typedef iterator_base< false > iterator;
//
//
///* Const iterator. Used to iterate through a container. Not able to modify 
// * the pointed to object T. */
//typedef iterator_base< true > const_iterator;
//
//// ------------------------------------------
//
//// --- Constructors and destructor PUBLIC ---
//
//// ------------------------------------------
//
//
///* Create with allocator. Construct empty container, allocator. */
//explicit my_container(const allocator_type& alloc)
//             : m_NodeAlloc(alloc), m_pFirst(0), m_pLast(0) {}
//
//
//private:
//// -------------------------------------------
//
//// --- Create/destroy node methods PRIVATE ---
//
//// -------------------------------------------
//
//
///* Create node.
// * We only allocate space for the node, but don't call the constructor for 
// * 'elem'. At least it seems like that's the way STL container classes do
// * it!*/
//node_pointer create_node(const_reference elem)
//{
//    node_pointer pNode = m_NodeAlloc.allocate(1);// Allocate space for 1 node
//
//    m_NodeAlloc.construct(pNode, node_value_type(elem));
//    return pNode;
//}
//
///** Delete node.*/
//void    delete_node(node_pointer pNode)
//{
//    m_NodeAlloc.destroy(pNode);
//    m_NodeAlloc.deallocate(pNode, 1);  // De-allocate space for 1 node
//
//}
//
//
//// --------------------------------
//
//// --- Member variables PRIVATE ---
//
//// --------------------------------
//
//node_allocator        m_NodeAlloc;    //< Allocator for the nodes.
//
//node_pointer        m_pFirst;    //< Points to FIRST node in  list
//
//node_pointer        m_pLast;    //< Points to LAST actual node in list
//
//};

# endif