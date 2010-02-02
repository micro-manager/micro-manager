#pragma once

typedef unsigned short uint16_t;

class ROIRectangle
{
public:
	ROIRectangle(void);
	~ROIRectangle(void);
	const uint16_t top() const;
	void top(const uint16_t& );
	const uint16_t top() const;
	void bottom(const uint16_t& );
	const uint16_t bottom() const;
	void left(const uint16_t& );
	const uint16_t left() const;
	void left(const uint16_t& );
	// use default operator=

	bool operator==(const ROIRectangle&)const;
private:
	class ROIImpl;
	ROIImpl* pROIImpl;

};
