#include "PvRoiCollection.h"

#include <stdexcept>

#include <vector>

//=============================================================================

PvRoiCollection::PvRoiCollection() :
    m_capacity(0),
    m_rois(),
    m_impliedRoi(0,0,0,0,1,1),
    m_rgnTypeArray(NULL)
{
    Clear();
}

PvRoiCollection::PvRoiCollection(const PvRoiCollection& other) :
    m_capacity(other.m_capacity),
    m_rois(other.m_rois),
    m_impliedRoi(other.m_impliedRoi),
    m_rgnTypeArray(m_capacity > 0 ? new rgn_type[m_capacity] : NULL)
{
    std::copy(other.m_rgnTypeArray, other.m_rgnTypeArray + m_capacity, m_rgnTypeArray);
}

PvRoiCollection::~PvRoiCollection()
{
    delete[] m_rgnTypeArray;
}

//=============================================================================

PvRoiCollection& PvRoiCollection::operator=(PvRoiCollection other)
{
    swap(*this, other);
    return *this;
}

void PvRoiCollection::swap(PvRoiCollection& first, PvRoiCollection& second)
{
    std::swap(first.m_capacity, second.m_capacity);
    std::swap(first.m_rois, second.m_rois);
    std::swap(first.m_impliedRoi, second.m_impliedRoi);
    std::swap(first.m_rgnTypeArray, second.m_rgnTypeArray);
}

//=============================================================================

void PvRoiCollection::SetCapacity(unsigned int capacity)
{
    m_rois.reserve(capacity);
    delete[] m_rgnTypeArray;
    m_rgnTypeArray = new rgn_type[capacity]();
    m_capacity = capacity;

    Clear();
}

void PvRoiCollection::Add(const PvRoi& newRoi)
{
    const unsigned int oldCount = Count();
    if (m_capacity == oldCount)
        throw std::length_error("Insufficient capacity");

    // Add the ROI to our internal array
    m_rois.push_back(newRoi);

    // Convert the new ROI to the PVCAM-specific type and add it to the array
    m_rgnTypeArray[oldCount] = newRoi.ToRgnType();

    // Recalculate the implied ROI
    updateImpliedRoi();
}

PvRoi PvRoiCollection::At(unsigned int index) const
{
    return m_rois.at(index); // can throw the out_of_range exc.
}

void PvRoiCollection::Clear()
{
    m_rois.clear();

    m_impliedRoi.SetSensorRgn(
        (std::numeric_limits<uns16>::max)(),
        (std::numeric_limits<uns16>::max)(),
        (std::numeric_limits<uns16>::min)(),
        (std::numeric_limits<uns16>::min)());

    memset(m_rgnTypeArray, 0, sizeof(rgn_type) * m_capacity);
}

unsigned int PvRoiCollection::Count() const
{
    return static_cast<unsigned int>(m_rois.size());
}

void PvRoiCollection::SetBinningX(uns16 bin)
{
    const unsigned int count = Count();
    for (unsigned int i = 0; i < count; ++i)
    {
        m_rois[i].SetBinningX(bin);
        m_rgnTypeArray[i].sbin = bin;
    }
    m_impliedRoi.SetBinningX(bin);
}
void PvRoiCollection::SetBinningY(uns16 bin)
{
    const unsigned int count = Count();
    for (unsigned int i = 0; i < count; ++i)
    {
        m_rois[i].SetBinningY(bin);
        m_rgnTypeArray[i].pbin = bin;
    }
    m_impliedRoi.SetBinningY(bin);
}
void PvRoiCollection::SetBinning(uns16 bX, uns16 bY)
{
    const unsigned int count = Count();
    for (unsigned int i = 0; i < count; ++i)
    {
        m_rois[i].SetBinning(bX, bY);
        m_rgnTypeArray[i].sbin = bX;
        m_rgnTypeArray[i].pbin = bY;
    }
    m_impliedRoi.SetBinning(bX, bY);
}

void PvRoiCollection::AdjustCoords()
{
    const unsigned int count = Count();
    for (unsigned int i = 0; i < count; ++i)
    {
        m_rois[i].AdjustCoords();
        m_rgnTypeArray[i] = m_rois[i].ToRgnType();
    }

}

uns16 PvRoiCollection::BinX() const
{
    return m_rois[0].BinX();
}

uns16 PvRoiCollection::BinY() const
{
    return m_rois[0].BinY();
}

PvRoi PvRoiCollection::ImpliedRoi() const
{
    return m_impliedRoi;
}

rgn_type* PvRoiCollection::ToRgnArray() const
{
    return m_rgnTypeArray;
}

bool PvRoiCollection::IsValid(uns16 sensorWidth, uns16 sensorHeight) const
{
    const unsigned int count = Count();
    for (unsigned int i = 0; i < count; ++i)
    {
        if (!m_rois[i].IsValid(sensorWidth, sensorHeight))
            return false;
    }
    return true;
}

bool PvRoiCollection::Equals(const PvRoiCollection& other) const
{
    const unsigned int count = Count();
    if (count != other.Count())
        return false;

    for (unsigned int i = 0; i < count; ++i)
    {
        if (!m_rois[i].Equals(other.At(i)))
            return false;
    }
    return true;
}

//=================================================================== PROTECTED

void PvRoiCollection::updateImpliedRoi()
{
    const unsigned int count = Count();

    // Adjust the implied ROI
    m_impliedRoi = m_rois[0];

    for (unsigned int i = 0; i < count; ++i)
    {
        const PvRoi& roi = m_rois[i];
        const uns16 implX1 = (std::min)(roi.SensorRgnX(), m_impliedRoi.SensorRgnX());
        const uns16 implY1 = (std::min)(roi.SensorRgnY(), m_impliedRoi.SensorRgnY());
        const uns16 implX2 = (std::max)(roi.SensorRgnX2(), m_impliedRoi.SensorRgnX2());
        const uns16 implY2 = (std::max)(roi.SensorRgnY2(), m_impliedRoi.SensorRgnY2());
        m_impliedRoi.SetSensorRgn(implX1, implY1, implX2 - implX1 + 1, implY2 - implY1 + 1);
    }
}