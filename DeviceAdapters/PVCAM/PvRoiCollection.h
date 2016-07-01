#ifndef _PVROICOLLECTION_H_
#define _PVROICOLLECTION_H_

#include "PVCAMIncludes.h"
#include "PvRoi.h"

#include <vector>

/**
* A container for a number of ROIs.
*/
class PvRoiCollection
{
public:
    /**
    * Creates a new ROI container.
    */
    PvRoiCollection();

    /**
    * Copy constructor. Required to make this class easily copiable.
    * @param Another collection to copy from.
    */
    PvRoiCollection(const PvRoiCollection& other);

    /**
    * Destructor. Frees all allocated resources.
    */
    ~PvRoiCollection();

    /**
    * Assignment operator. To complete the rule of three.
    */
    PvRoiCollection& operator=(PvRoiCollection other);

    /**
    * Using the copy & swap idiom.
    */
    void swap(PvRoiCollection& first, PvRoiCollection& second);

    /**
    * Sets the collection capacity.
    * @param capacity Maximum number of elements the collection can hold.
    */
    void SetCapacity(unsigned int capacity);

    /**
    * Adds a new ROI to the collection
    * @param newRoi A ROI to add.
    * @throw length_error if the capacity is not sufficient
    */
    void Add(const PvRoi& newRoi);

    /**
    * Returns ROI at given index.
    * @param index.
    * @return ROI object.
    * @throw out_of_range exception if the index is invalid.
    */
    PvRoi At(unsigned int index) const;

    /**
    * Erases all ROIs from the collection.
    */
    void Clear();

    /**
    * Returns current number of ROIs in the collection.
    * @return Current number of ROIs.
    */
    unsigned int Count() const;

    /**
    * Sets the X-binning to all ROIs.
    * @param bin Binning value.
    */
    void SetBinningX(uns16 bin);
    /**
    * Sets the Y-binning to all ROIs.
    * @param bin Binning value.
    */
    void SetBinningY(uns16 bin);
    /**
    * Sets the binning to all ROIs.
    * @param bx X-binning value.
    * @param by Y-binning value.
    */
    void SetBinning(uns16 bX, uns16 bY);

    /**
    * Adjusts the coordinates to actual binning factor
    */
    void AdjustCoords();

    /**
    * Returns the X-binning value common to all ROIs.
    * @return Binning value.
    */
    uns16 BinX() const;
    /**
    * Returns the Y-binning value common to all ROIs.
    * @return Binning value.
    */
    uns16 BinY() const;

    /**
    * Returns the implied ROI - i.e. a sensor area that
    * contains all the ROIs.
    * @return Roi object.
    */
    PvRoi ImpliedRoi() const;

    /**
    * Return an array of PVCAM rgn_types, used in pl_exp_setup() functions.
    * @return Array of PVCAM specific region types.
    */
    rgn_type* ToRgnArray() const;

    /**
    * Validates all ROIs - checks whether all ROIs fit into given sensor dimensions.
    * @param sensorWidth A width of the imaging sensor in pixels.
    * @param sensorHeight A height of the imaging sensor in pixels.
    * @retun True if all ROIs in the collection are valid. False otherwise.
    */
    bool IsValid(uns16 sensorWidth, uns16 sensorHeight) const;

    /**
    * Checks whether this collection is equal to other collection.
    * @param other Other collection to check the current against.
    * @return True is all ROI coordinates and their definitions are the same as in the other collection.
    *         False otherwise.
    */
    bool Equals(const PvRoiCollection& other) const;

protected:
    /**
    * Re-calculates the implied ROI.
    */
    void updateImpliedRoi();

private:
    unsigned int       m_capacity;     ///< Maximum capacity of the collection.
    std::vector<PvRoi> m_rois;         ///< Current ROIs.
    PvRoi              m_impliedRoi;   ///< Pre-calculated implied ROI.

    rgn_type*          m_rgnTypeArray; ///< Prepared PVCAM region array.
};

#endif // _PVROICOLLECTION_H_